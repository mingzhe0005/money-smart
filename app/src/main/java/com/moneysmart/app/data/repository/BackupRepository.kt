package com.moneysmart.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.moneysmart.app.data.local.MoneySmartDatabase
import com.moneysmart.app.data.local.SettingsDataStore
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.BudgetEntity
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.ExchangeRateEntity
import com.moneysmart.app.data.local.entity.RecurringTransactionEntity
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class MoneySmartBackupSnapshot(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val baseCurrency: String = "USD",
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val recurringTransactions: List<RecurringTransactionEntity> = emptyList(),
    val exchangeRates: List<ExchangeRateEntity> = emptyList()
)

class BackupRepository(
    private val context: Context,
    private val db: MoneySmartDatabase,
    private val settingsDataStore: SettingsDataStore
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createBackupSnapshot(): MoneySmartBackupSnapshot = withContext(Dispatchers.IO) {
        val settings = settingsDataStore.getCurrentSettings()
        MoneySmartBackupSnapshot(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            baseCurrency = settings.baseCurrency,
            accounts = db.accountDao().getAllAccounts(),
            categories = db.categoryDao().getAllCategories(),
            transactions = db.transactionDao().getAllTransactions(),
            budgets = db.budgetDao().getAllBudgets(),
            recurringTransactions = db.recurringDao().getAllRecurring(),
            exchangeRates = db.exchangeRateDao().getAllRates()
        )
    }

    suspend fun exportJsonToTree(treeUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("Cannot access selected backup folder. Please re-select the folder.")
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "money_smart_backup_$stamp.json"
            val doc = dir.createFile("application/json", fileName)
                ?: error("Failed to create backup file in selected folder.")
            val snapshot = createBackupSnapshot()
            val serialized = json.encodeToString(snapshot)
            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                out.write(serialized.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: error("Unable to open output stream for backup file.")

            // Also keep a rolling latest file for quick verification
            settingsDataStore.setLastBackupTimestamp(System.currentTimeMillis())
            fileName
        }
    }

    suspend fun exportCsvToTree(treeUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("Cannot access selected folder.")
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "money_smart_transactions_$stamp.csv"
            val doc = dir.createFile("text/csv", fileName)
                ?: error("Failed to create CSV file.")

            val accounts = db.accountDao().getAllAccounts().associateBy { it.id }
            val categories = db.categoryDao().getAllCategories().associateBy { it.id }
            val transactions = db.transactionDao().getAllActiveTransactions()

            val sb = StringBuilder()
            sb.appendLine("ID,DateTime,Type,Amount,Currency,ExchangeRateToBase,BaseAmount,Account,ToAccount,Category,Merchant,Note,Source,NeedsReview")
            for (tx in transactions) {
                val dt = FormatUtils.formatIsoDateTime(tx.timestamp)
                val accName = accounts[tx.accountId]?.name.orEmpty()
                val toAccName = tx.toAccountId?.let { accounts[it]?.name }.orEmpty()
                val catName = tx.categoryId?.let { categories[it]?.name }.orEmpty()
                sb.appendLine(
                    listOf(
                        tx.id.toString(),
                        csvEscape(dt),
                        tx.type.name,
                        tx.amount.toString(),
                        csvEscape(tx.currency),
                        tx.exchangeRateToBase.toString(),
                        tx.baseAmount.toString(),
                        csvEscape(accName),
                        csvEscape(toAccName),
                        csvEscape(catName),
                        csvEscape(tx.merchant),
                        csvEscape(tx.note),
                        tx.source.name,
                        tx.needsReview.toString()
                    ).joinToString(",")
                )
            }

            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                out.write(sb.toString().toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: error("Unable to open CSV output stream.")
            fileName
        }
    }

    suspend fun restoreFromJsonUri(fileUri: Uri): Result<MoneySmartBackupSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val content = context.contentResolver.openInputStream(fileUri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("Unable to read backup file.")

            val snapshot = json.decodeFromString<MoneySmartBackupSnapshot>(content)
            db.withTransaction {
                db.transactionDao().deleteAll()
                db.budgetDao().deleteAll()
                db.recurringDao().deleteAll()
                db.categoryDao().deleteAll()
                db.accountDao().deleteAll()
                db.exchangeRateDao().deleteAll()

                if (snapshot.accounts.isNotEmpty()) db.accountDao().insertAll(snapshot.accounts)
                if (snapshot.categories.isNotEmpty()) db.categoryDao().insertAll(snapshot.categories)
                if (snapshot.transactions.isNotEmpty()) db.transactionDao().insertAll(snapshot.transactions)
                if (snapshot.budgets.isNotEmpty()) db.budgetDao().insertAll(snapshot.budgets)
                if (snapshot.recurringTransactions.isNotEmpty()) {
                    db.recurringDao().insertAll(snapshot.recurringTransactions)
                }
                if (snapshot.exchangeRates.isNotEmpty()) {
                    db.exchangeRateDao().upsertAll(snapshot.exchangeRates)
                }
            }
            if (snapshot.baseCurrency.isNotBlank()) {
                settingsDataStore.setBaseCurrency(snapshot.baseCurrency)
            }
            snapshot
        }
    }

    private fun csvEscape(value: String): String {
        val clean = value.replace("\r", " ").replace("\n", " ")
        return if (clean.contains(',') || clean.contains('"')) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else {
            clean
        }
    }
}
