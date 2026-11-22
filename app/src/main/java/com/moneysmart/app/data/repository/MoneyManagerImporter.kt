package com.moneysmart.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.moneysmart.app.data.local.CategoryOrganizer
import com.moneysmart.app.data.local.MoneySmartDatabase
import com.moneysmart.app.data.local.SettingsDataStore
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.CategoryType
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionSource
import com.moneysmart.app.data.local.entity.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

data class MoneyManagerImportResult(
    val importedCount: Int,
    val skippedDuplicateCount: Int,
    val skippedTransferInCount: Int,
    val createdAccounts: Int,
    val createdCategories: Int,
    val detectedBaseCurrency: String
)

class MoneyManagerImporter(
    private val context: Context,
    private val db: MoneySmartDatabase,
    private val settingsDataStore: SettingsDataStore
) {
    suspend fun importFromUri(uri: Uri): Result<MoneyManagerImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Unable to open selected Money Manager export file.")
            importFromBytes(bytes)
        }
    }

    suspend fun importFromBytes(bytes: ByteArray): MoneyManagerImportResult {
        val rows = parseSpreadsheetOrCsv(bytes)
        if (rows.size <= 1) {
            error("No transaction rows found in file.")
        }

        val header = rows.first()
        val colDate = findHeaderIndex(header, listOf("date"), 0)
        val colAccount = findHeaderIndex(header, listOf("account"), 1)
        val colCategory = findHeaderIndex(header, listOf("category"), 2)
        val colSubcategory = findHeaderIndex(header, listOf("subcategory"), 3)
        val colNote = findHeaderIndex(header, listOf("note"), 4)
        val colBaseAmount = 5.takeIf { header.size > 5 } ?: -1
        val colType = findHeaderIndex(header, listOf("income/expense", "type"), 6)
        val colTag = findHeaderIndex(header, listOf("tag"), 7)
        val colDesc = findHeaderIndex(header, listOf("description"), 8)
        val colAmount = findHeaderIndex(header, listOf("amount"), 9)
        val colCurrency = findHeaderIndex(header, listOf("currency"), 10)

        val currentSettings = settingsDataStore.getCurrentSettings()
        val headerBaseCurrency = header.getOrNull(colBaseAmount)
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.length == 3 && it.all(Char::isLetter) }
            ?: currentSettings.baseCurrency

        var importedCount = 0
        var skippedDuplicates = 0
        var skippedTransferIn = 0
        var createdAccounts = 0
        var createdCategories = 0

        db.withTransaction {
            val accountDao = db.accountDao()
            val categoryDao = db.categoryDao()
            val txDao = db.transactionDao()

            val accountsByName = accountDao.getAllAccounts()
                .associateBy { it.name.trim().lowercase() }
                .toMutableMap()

            val allCategories = categoryDao.getAllCategories().toMutableList()
            val existingTransactions = txDao.getAllActiveTransactions()
            val existingKeys = existingTransactions.mapTo(mutableSetOf()) { tx ->
                "${tx.timestamp / 2000L}|${"%.2f".format(tx.amount)}|${tx.currency.uppercase()}|${tx.accountId}"
            }

            suspend fun getOrCreateAccount(name: String, preferredCurrency: String): AccountEntity {
                val cleanName = name.trim().ifBlank { "Cash" }
                val key = cleanName.lowercase()
                accountsByName[key]?.let { return it }

                val inferredType = inferAccountType(cleanName)
                val inferredCurrency = inferAccountCurrency(cleanName, preferredCurrency, headerBaseCurrency)
                val created = AccountEntity(
                    name = cleanName,
                    type = inferredType,
                    currency = inferredCurrency,
                    initialBalance = 0.0,
                    iconName = iconForAccountType(inferredType),
                    colorHex = 0xFF0D6E52L
                )
                val id = accountDao.insert(created)
                val saved = created.copy(id = id)
                accountsByName[key] = saved
                createdAccounts++
                return saved
            }

            suspend fun getOrCreateCategory(
                categoryName: String,
                subcategoryName: String,
                type: CategoryType
            ): Long? {
                if (categoryName.isBlank()) return null
                val parentClean = CategoryOrganizer.canonicalName(categoryName, type)

                var parent = allCategories.firstOrNull {
                    it.parentId == null &&
                        it.type == type &&
                        CategoryOrganizer.canonicalName(it.name, type).equals(parentClean, ignoreCase = true)
                }
                if (parent == null) {
                    val palette = listOf(
                        0xFFEF4444L, 0xFFF59E0BL, 0xFF10B981L, 0xFF0EA5E9L,
                        0xFF8B5CF6L, 0xFFEC4899L, 0xFF14B8A6L, 0xFF6366F1L,
                        0xFFF97316L, 0xFF06B6D4L, 0xFFF43F5EL, 0xFF84CC16L
                    )
                    val spec = CategoryOrganizer.canonicalSpec(parentClean, type)
                    val colorHex = spec?.colorHex ?: if (type == CategoryType.INCOME) {
                        0xFF059669L
                    } else {
                        palette[allCategories.size % palette.size]
                    }
                    val newParent = CategoryEntity(
                        name = parentClean,
                        type = type,
                        parentId = null,
                        iconName = spec?.iconName ?: if (type == CategoryType.INCOME) "Payments" else "Category",
                        colorHex = colorHex,
                        sortOrder = allCategories.size + 1
                    )
                    val id = categoryDao.insert(newParent)
                    parent = newParent.copy(id = id)
                    allCategories.add(parent)
                    createdCategories++
                }

                if (subcategoryName.isBlank()) return parent.id
                val subClean = CategoryOrganizer.cleanSubcategoryName(subcategoryName)

                var child = allCategories.firstOrNull {
                    it.parentId == parent.id &&
                        it.type == type &&
                        CategoryOrganizer.cleanSubcategoryName(it.name).equals(subClean, ignoreCase = true)
                }
                if (child == null) {
                    val newChild = CategoryEntity(
                        name = subClean,
                        type = type,
                        parentId = parent.id,
                        iconName = parent.iconName,
                        colorHex = parent.colorHex,
                        sortOrder = allCategories.size + 1
                    )
                    val childId = categoryDao.insert(newChild)
                    child = newChild.copy(id = childId)
                    allCategories.add(child)
                    createdCategories++
                }
                return child.id
            }

            val toInsert = mutableListOf<TransactionEntity>()

            for (i in 1 until rows.size) {
                val row = rows[i]
                if (row.all { it.isBlank() }) continue

                val typeStr = row.getOrNull(colType)?.trim().orEmpty()
                if (typeStr.equals("Transfer-In", ignoreCase = true)) {
                    skippedTransferIn++
                    continue
                }

                val txType = when {
                    typeStr.equals("Income", ignoreCase = true) -> TransactionType.INCOME
                    typeStr.startsWith("Transfer", ignoreCase = true) -> TransactionType.TRANSFER
                    typeStr.equals("Expense", ignoreCase = true) -> TransactionType.EXPENSE
                    else -> TransactionType.EXPENSE
                }

                val rawDate = row.getOrNull(colDate)?.trim().orEmpty()
                val timestamp = parseDateCell(rawDate) ?: continue

                val amountVal = row.getOrNull(colAmount)?.toDoubleOrNull()?.let(::abs)
                    ?: row.getOrNull(colBaseAmount)?.toDoubleOrNull()?.let(::abs)
                    ?: continue
                if (amountVal <= 0.0) continue

                val baseAmountVal = row.getOrNull(colBaseAmount)?.toDoubleOrNull()?.let(::abs) ?: amountVal
                val currencyCode = row.getOrNull(colCurrency)
                    ?.trim()
                    ?.uppercase()
                    ?.takeIf { it.length == 3 && it.all(Char::isLetter) }
                    ?: headerBaseCurrency

                val rateToBase = if (currencyCode.equals(headerBaseCurrency, ignoreCase = true) || amountVal <= 0.0) {
                    1.0
                } else {
                    baseAmountVal / amountVal
                }

                val accountName = row.getOrNull(colAccount)?.trim().orEmpty()
                val sourceAccount = getOrCreateAccount(accountName, currencyCode)

                val categoryCell = row.getOrNull(colCategory)?.trim().orEmpty()
                val subCategoryCell = row.getOrNull(colSubcategory)?.trim().orEmpty()

                var toAccountId: Long? = null
                var categoryId: Long? = null

                if (txType == TransactionType.TRANSFER) {
                    if (categoryCell.isNotBlank()) {
                        toAccountId = getOrCreateAccount(categoryCell, currencyCode).id
                    }
                } else {
                    val catType = if (txType == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
                    categoryId = getOrCreateCategory(categoryCell, subCategoryCell, catType)
                }

                val merchantText = row.getOrNull(colNote)?.trim().orEmpty()
                val tagText = row.getOrNull(colTag)?.trim().orEmpty()
                val descText = row.getOrNull(colDesc)?.trim().orEmpty()
                val extraNote = listOfNotNull(
                    tagText.takeIf { it.isNotEmpty() },
                    descText.takeIf { it.isNotEmpty() }
                ).joinToString(" · ")

                val dedupKey = "${timestamp / 2000L}|${"%.2f".format(amountVal)}|${currencyCode}|${sourceAccount.id}"
                if (dedupKey in existingKeys) {
                    skippedDuplicates++
                    continue
                }
                existingKeys.add(dedupKey)

                toInsert.add(
                    TransactionEntity(
                        type = txType,
                        amount = amountVal,
                        currency = currencyCode,
                        exchangeRateToBase = rateToBase,
                        baseAmount = LedgerCalculations.computeBaseAmount(amountVal, rateToBase),
                        accountId = sourceAccount.id,
                        toAccountId = toAccountId,
                        categoryId = categoryId,
                        merchant = merchantText,
                        note = extraNote,
                        timestamp = timestamp,
                        source = TransactionSource.MANUAL,
                        needsReview = false
                    )
                )
                importedCount++
            }

            if (toInsert.isNotEmpty()) {
                txDao.insertAll(toInsert)
            }
        }

        if (headerBaseCurrency != currentSettings.baseCurrency) {
            settingsDataStore.setBaseCurrency(headerBaseCurrency)
        }

        return MoneyManagerImportResult(
            importedCount = importedCount,
            skippedDuplicateCount = skippedDuplicates,
            skippedTransferInCount = skippedTransferIn,
            createdAccounts = createdAccounts,
            createdCategories = createdCategories,
            detectedBaseCurrency = headerBaseCurrency
        )
    }

    companion object {
        fun parseSpreadsheetOrCsv(bytes: ByteArray): List<List<String>> {
            val isZip = bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte()
            return if (isZip) {
                val tmpFile = File.createTempFile("mm_import_", ".xlsx")
                try {
                    tmpFile.writeBytes(bytes)
                    parseXlsxFile(tmpFile)
                } finally {
                    tmpFile.delete()
                }
            } else {
                parseDelimitedText(bytes.toString(Charsets.UTF_8))
            }
        }

        fun parseXlsxFile(file: File): List<List<String>> {
            var sharedStringsBytes: ByteArray? = null
            var firstSheetBytes: ByteArray? = null

            // ZipFile reads from the Central Directory, safely supporting Excel ZIP64 data descriptors
            // that cause ZipInputStream to throw "invalid entry size (expected 0...)".
            ZipFile(file).use { zip ->
                val ssEntry = zip.getEntry("xl/sharedStrings.xml")
                if (ssEntry != null) {
                    sharedStringsBytes = zip.getInputStream(ssEntry).use { it.readBytes() }
                }
                val sheet1Entry = zip.getEntry("xl/worksheets/sheet1.xml")
                    ?: zip.entries().asSequence().firstOrNull { entry ->
                        entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml")
                    }
                if (sheet1Entry != null) {
                    firstSheetBytes = zip.getInputStream(sheet1Entry).use { it.readBytes() }
                }
            }

            val sheetData = firstSheetBytes ?: error("Missing worksheet XML in Excel file.")
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val builder = factory.newDocumentBuilder()

            val sharedStrings = if (sharedStringsBytes != null) {
                val doc = builder.parse(ByteArrayInputStream(sharedStringsBytes))
                val siNodes = doc.getElementsByTagNameNS("*", "si")
                List(siNodes.length) { i ->
                    val si = siNodes.item(i) as Element
                    val tNodes = si.getElementsByTagNameNS("*", "t")
                    buildString {
                        for (j in 0 until tNodes.length) {
                            append(tNodes.item(j).textContent.orEmpty())
                        }
                    }
                }
            } else {
                emptyList()
            }

            val sheetDoc = builder.parse(ByteArrayInputStream(sheetData))
            val rowNodes = sheetDoc.getElementsByTagNameNS("*", "row")
            val rows = mutableListOf<List<String>>()

            for (rIdx in 0 until rowNodes.length) {
                val rowElem = rowNodes.item(rIdx) as Element
                val cellNodes = rowElem.getElementsByTagNameNS("*", "c")
                val rowMap = mutableMapOf<Int, String>()
                var maxCol = -1

                for (cIdx in 0 until cellNodes.length) {
                    val cell = cellNodes.item(cIdx) as Element
                    val colRef = cell.getAttribute("r")
                    val colIndex = if (colRef.isNotBlank()) columnRefToIndex(colRef) else cIdx
                    if (colIndex > maxCol) maxCol = colIndex

                    val typeAttr = cell.getAttribute("t")
                    val value = when (typeAttr) {
                        "s" -> {
                            val idx = cell.getElementsByTagNameNS("*", "v").item(0)?.textContent?.trim()?.toIntOrNull()
                            if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                        }
                        "inlineStr" -> {
                            val tNodes = cell.getElementsByTagNameNS("*", "t")
                            buildString {
                                for (k in 0 until tNodes.length) {
                                    append(tNodes.item(k).textContent.orEmpty())
                                }
                            }
                        }
                        else -> cell.getElementsByTagNameNS("*", "v").item(0)?.textContent.orEmpty()
                    }
                    rowMap[colIndex] = value
                }

                if (maxCol >= 0) {
                    rows.add(List(maxCol + 1) { idx -> rowMap[idx].orEmpty() })
                }
            }
            return rows
        }

        fun parseDelimitedText(text: String): List<List<String>> {
            val lines = text.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return emptyList()
            val delimiter = if (lines.first().count { it == '\t' } >= lines.first().count { it == ',' }) '\t' else ','
            return lines.map { line -> splitCsvLine(line, delimiter) }
        }

        private fun splitCsvLine(line: String, delimiter: Char): List<String> {
            val cells = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                when {
                    ch == '"' -> {
                        if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                            current.append('"')
                            i++
                        } else {
                            inQuotes = !inQuotes
                        }
                    }
                    ch == delimiter && !inQuotes -> {
                        cells.add(current.toString())
                        current.clear()
                    }
                    else -> current.append(ch)
                }
                i++
            }
            cells.add(current.toString())
            return cells
        }

        fun columnRefToIndex(ref: String): Int {
            var col = 0
            for (ch in ref) {
                if (ch.isLetter()) {
                    col = col * 26 + (ch.uppercaseChar() - 'A' + 1)
                } else {
                    break
                }
            }
            return (col - 1).coerceAtLeast(0)
        }

        fun excelSerialToEpochMillis(serial: Double, zoneId: ZoneId = ZoneId.systemDefault()): Long {
            val wholeDays = serial.toLong()
            val fraction = (serial - wholeDays).coerceIn(0.0, 0.999999)
            val date = LocalDate.of(1899, 12, 30).plusDays(wholeDays)
            val nanos = (fraction * 86_400_000_000_000.0).toLong().coerceIn(0L, 86_399_999_999_999L)
            val time = LocalTime.ofNanoOfDay(nanos)
            return ZonedDateTime.of(date, time, zoneId).toInstant().toEpochMilli()
        }

        fun parseDateCell(raw: String, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            trimmed.toDoubleOrNull()?.let { serial ->
                if (serial > 10_000.0) {
                    return excelSerialToEpochMillis(serial, zoneId)
                }
            }
            val dateTimePatterns = listOf(
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "MM/dd/yyyy HH:mm:ss",
                "MM/dd/yyyy HH:mm"
            )
            for (pattern in dateTimePatterns) {
                val parsed = runCatching {
                    LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern(pattern))
                        .atZone(zoneId).toInstant().toEpochMilli()
                }.getOrNull()
                if (parsed != null) return parsed
            }
            val dateOnlyPatterns = listOf("yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy")
            for (pattern in dateOnlyPatterns) {
                val parsed = runCatching {
                    LocalDate.parse(trimmed, DateTimeFormatter.ofPattern(pattern))
                        .atStartOfDay(zoneId).toInstant().toEpochMilli()
                }.getOrNull()
                if (parsed != null) return parsed
            }
            return runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()
        }

        private fun findHeaderIndex(header: List<String>, candidates: List<String>, fallback: Int): Int {
            for (candidate in candidates) {
                val idx = header.indexOfFirst { it.trim().equals(candidate, ignoreCase = true) }
                if (idx >= 0) return idx
            }
            return fallback
        }

        private fun inferAccountType(name: String): AccountType {
            val lower = name.lowercase()
            return when {
                lower.contains("invest") || lower.contains("gold") -> AccountType.INVESTMENT
                lower.contains("coin") || lower.contains("note") || lower.contains("cash") || lower.contains("bht") -> AccountType.CASH
                lower.contains("paylah") || lower.contains("youtrip") || lower.contains("wise") ||
                    lower.contains("voucher") || lower.contains("lifesg") || lower.contains("timbre") -> AccountType.E_WALLET
                lower.contains("card") -> AccountType.CREDIT_CARD
                else -> AccountType.BANK
            }
        }

        private fun inferAccountCurrency(name: String, txCurrency: String, baseCurrency: String): String {
            val lower = name.lowercase()
            return when {
                lower.contains("euro") -> "EUR"
                lower.contains("yen") -> "JPY"
                lower.contains("bht") || lower.contains("thai") -> "THB"
                lower.contains("sgd") -> "SGD"
                else -> txCurrency.ifBlank { baseCurrency }
            }
        }

        private fun iconForAccountType(type: AccountType): String = when (type) {
            AccountType.CASH -> "AccountBalanceWallet"
            AccountType.BANK -> "AccountBalance"
            AccountType.CREDIT_CARD -> "CreditCard"
            AccountType.E_WALLET -> "Smartphone"
            AccountType.INVESTMENT -> "TrendingUp"
            AccountType.OTHER -> "AccountBalanceWallet"
        }
    }
}
