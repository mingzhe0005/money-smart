package com.moneysmart.app.data.repository

import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.data.local.entity.RecurrenceFrequency
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionType
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToLong

object LedgerCalculations {

    const val DEFAULT_DEDUP_WINDOW_MILLIS = 120_000L
    const val CROSS_APP_MERCHANT_DEDUP_WINDOW_MILLIS = 900_000L
    const val TRANSFER_PAIRING_WINDOW_MILLIS = 300_000L

    data class TransferPairMatch(
        val existing: TransactionEntity,
        val sourceAccountId: Long,
        val destAccountId: Long,
        val shouldUpdateExisting: Boolean
    )

    private val DEDUP_STOPWORDS = setOf(
        "debit", "credit", "card", "payment", "purchase", "singapore",
        "wallet", "bank", "transfer", "online", "charged",
        "frank", "ocbc", "dbs", "posb", "uob", "samsung"
    )

    private val GENERIC_ACCOUNT_TOKENS = setOf(
        "bank", "account", "acc", "acct", "savings", "saving",
        "checking", "current", "card", "credit", "debit", "wallet",
        "ewallet", "cash", "my", "main", "personal", "primary",
        "sgd", "usd", "myr", "eur", "gbp", "jpy", "default"
    )

    private val OUTGOING_TRANSFER_PHRASES = listOf(
        "transfer", "transferred", "fast", "paynow", "giro",
        "top up", "top-up", "topup", "remit", "fund transfer",
        "interbank", "own account", "sent to"
    )

    private val TOP_UP_PHRASES = listOf("top up", "top-up", "topup")

    private val CREDIT_CARD_PAYMENT_PHRASES = listOf(
        "credit card", "card bill", "card payment"
    )

    fun computeBaseAmount(amount: Double, exchangeRateToBase: Double): Double {
        val raw = amount * exchangeRateToBase
        return (raw * 100.0).roundToLong() / 100.0
    }

    fun effectiveExpenseBaseAmount(tx: TransactionEntity): Double {
        if (tx.isDeleted || tx.type != TransactionType.EXPENSE) return 0.0
        val net = (tx.baseAmount - tx.reimbursedBaseAmount).coerceAtLeast(0.0)
        return (net * 100.0).roundToLong() / 100.0
    }

    fun effectiveIncomeBaseAmount(tx: TransactionEntity): Double {
        if (tx.isDeleted || tx.type != TransactionType.INCOME || tx.isReimbursement) return 0.0
        return tx.baseAmount
    }

    fun remainingReimbursementAmount(tx: TransactionEntity): Double {
        if (tx.isDeleted || tx.type != TransactionType.INCOME) return 0.0
        if (tx.splitBillId != null && tx.reimbursedAmount <= 0.005) return 0.0
        return ((tx.amount - tx.reimbursedAmount).coerceAtLeast(0.0) * 100.0).roundToLong() / 100.0
    }

    fun remainingReimbursementBaseAmount(tx: TransactionEntity): Double {
        if (tx.isDeleted || tx.type != TransactionType.INCOME) return 0.0
        if (tx.splitBillId != null && tx.reimbursedBaseAmount <= 0.005) return 0.0
        return ((tx.baseAmount - tx.reimbursedBaseAmount).coerceAtLeast(0.0) * 100.0).roundToLong() / 100.0
    }

    fun unlinkedReimbursementBaseAmount(tx: TransactionEntity): Double {
        if (tx.isDeleted || tx.type != TransactionType.INCOME || !tx.isReimbursement) return 0.0
        return remainingReimbursementBaseAmount(tx)
    }

    fun effectiveTotalExpensesBase(transactions: List<TransactionEntity>): Double {
        val gross = transactions.sumOf { effectiveExpenseBaseAmount(it) }
        val offset = transactions.sumOf { unlinkedReimbursementBaseAmount(it) }
        return ((gross - offset).coerceAtLeast(0.0) * 100.0).roundToLong() / 100.0
    }

    /**
     * Splits [totalAmount] equally among 1 (you) + [friendCount] friends,
     * keeping each share rounded to 2 decimals and ensuring exact sum == totalAmount.
     */
    fun computeEqualSplitShares(totalAmount: Double, friendCount: Int): Pair<Double, List<Double>> {
        if (friendCount <= 0 || totalAmount <= 0.0) return totalAmount.coerceAtLeast(0.0) to emptyList()
        val totalCents = (totalAmount * 100.0).roundToLong()
        val people = 1 + friendCount
        val baseCents = totalCents / people
        val remainder = (totalCents % people).toInt()
        val friendShares = List(friendCount) { idx ->
            val extra = if (idx < remainder) 1L else 0L
            (baseCents + extra) / 100.0
        }
        val friendsSumCents = friendShares.sumOf { (it * 100.0).roundToLong() }
        val myShare = (totalCents - friendsSumCents) / 100.0
        return myShare to friendShares
    }

    /**
     * Converts a stored API rate (`1 baseCurrency = apiRate targetCurrency`) into
     * `exchangeRateToBase` (`1 targetCurrency = exchangeRateToBase baseCurrency`).
     */
    fun exchangeRateToBaseFromApiRate(
        txCurrency: String,
        baseCurrency: String,
        apiRateBaseToTx: Double?
    ): Double {
        if (txCurrency.equals(baseCurrency, ignoreCase = true)) return 1.0
        if (apiRateBaseToTx == null || apiRateBaseToTx <= 0.0) return 1.0
        return 1.0 / apiRateBaseToTx
    }

    fun computeAccountBalance(
        account: AccountEntity,
        transactions: List<TransactionEntity>,
        baseCurrency: String = account.currency
    ): Double {
        var balance = account.initialBalance
        for (tx in transactions) {
            if (tx.isDeleted) continue
            val delta = when {
                tx.currency.equals(account.currency, ignoreCase = true) -> tx.amount
                account.currency.equals(baseCurrency, ignoreCase = true) -> tx.baseAmount
                else -> tx.baseAmount
            }
            when (tx.type) {
                TransactionType.EXPENSE -> {
                    if (tx.accountId == account.id) balance -= delta
                }
                TransactionType.INCOME -> {
                    if (tx.accountId == account.id) balance += delta
                }
                TransactionType.TRANSFER -> {
                    if (tx.accountId == account.id) balance -= delta
                    if (tx.toAccountId == account.id) balance += delta
                }
            }
        }
        return (balance * 100.0).roundToLong() / 100.0
    }

    fun computeAutoRechargeShortfall(
        currentBalance: Double,
        minBalance: Double = 0.0
    ): Double {
        if (currentBalance >= minBalance - 0.005) return 0.0
        val raw = minBalance - currentBalance
        return (raw * 100.0).roundToLong() / 100.0
    }

    fun computeCalibratedInitialBalance(
        account: AccountEntity,
        transactions: List<TransactionEntity>,
        targetCurrentBalance: Double,
        baseCurrency: String = account.currency
    ): Double {
        val computedNow = computeAccountBalance(account, transactions, baseCurrency)
        val txNetDelta = computedNow - account.initialBalance
        val calibrated = targetCurrentBalance - txNetDelta
        return (calibrated * 100.0).roundToLong() / 100.0
    }

    fun computeCreditCardOwed(currentBalance: Double): Double {
        return ((-currentBalance).coerceAtLeast(0.0) * 100.0).roundToLong() / 100.0
    }

    fun computeCreditCardAvailable(currentBalance: Double, creditLimit: Double?): Double? {
        if (creditLimit == null || creditLimit <= 0.0) return null
        val available = (creditLimit - computeCreditCardOwed(currentBalance)).coerceAtLeast(0.0)
        return (available * 100.0).roundToLong() / 100.0
    }

    fun computeCreditCardUtilization(currentBalance: Double, creditLimit: Double?): Float? {
        if (creditLimit == null || creditLimit <= 0.0) return null
        return (computeCreditCardOwed(currentBalance) / creditLimit).toFloat().coerceAtLeast(0f)
    }

    fun normalizeCardLast4(raw: String?): String? {
        return raw?.filter { it.isDigit() }?.takeIf { it.length == 4 }
    }

    private fun normalizeMerchantForDedup(raw: String?): String {
        return raw.orEmpty()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    fun merchantsMatchForDedup(a: String?, b: String?): Boolean {
        val normA = normalizeMerchantForDedup(a)
        val normB = normalizeMerchantForDedup(b)
        if (normA.isBlank() || normB.isBlank()) return false
        if (normA == normB) return true

        val shorter = if (normA.length <= normB.length) normA else normB
        val longer = if (normA.length <= normB.length) normB else normA
        if (shorter.length >= 4 && (longer.startsWith(shorter) || longer.contains(shorter))) {
            return true
        }

        val tokensA = normA.split(' ').filter { it.length >= 4 && it !in DEDUP_STOPWORDS }.toSet()
        val tokensB = normB.split(' ').filter { it.length >= 4 && it !in DEDUP_STOPWORDS }.toSet()
        return tokensA.isNotEmpty() && tokensA.any { it in tokensB }
    }

    fun findDuplicateTransaction(
        existing: List<TransactionEntity>,
        amount: Double,
        currency: String,
        timestamp: Long,
        windowMillis: Long = DEFAULT_DEDUP_WINDOW_MILLIS,
        type: TransactionType? = null,
        merchant: String? = null,
        crossAppMerchantWindowMillis: Long = CROSS_APP_MERCHANT_DEDUP_WINDOW_MILLIS,
        isDifferentSourceApp: (TransactionEntity) -> Boolean = { false }
    ): TransactionEntity? {
        return existing.firstOrNull { tx ->
            if (tx.isDeleted ||
                abs(tx.amount - amount) >= 0.005 ||
                !tx.currency.equals(currency, ignoreCase = true) ||
                (type != null && tx.type != type)
            ) {
                return@firstOrNull false
            }
            val diffMillis = abs(tx.timestamp - timestamp)
            diffMillis <= windowMillis ||
                (diffMillis <= crossAppMerchantWindowMillis &&
                    isDifferentSourceApp(tx) &&
                    merchantsMatchForDedup(tx.merchant, merchant))
        }
    }

    fun computeNextDueMillis(
        currentDueMillis: Long,
        frequency: RecurrenceFrequency,
        intervalCount: Int = 1,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val steps = intervalCount.coerceAtLeast(1).toLong()
        val zoned = Instant.ofEpochMilli(currentDueMillis).atZone(zoneId)
        val next = when (frequency) {
            RecurrenceFrequency.DAILY -> zoned.plusDays(steps)
            RecurrenceFrequency.WEEKLY -> zoned.plusWeeks(steps)
            RecurrenceFrequency.MONTHLY -> zoned.plusMonths(steps)
            RecurrenceFrequency.YEARLY -> zoned.plusYears(steps)
        }
        return next.toInstant().toEpochMilli()
    }

    private fun distinctiveAccountTokens(account: AccountEntity): List<String> {
        val rawTokens = account.name.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in GENERIC_ACCOUNT_TOKENS }
        val expanded = linkedSetOf<String>()
        for (token in rawTokens) {
            expanded.add(token)
            if (token.endsWith("bank") && token.length >= 8) {
                expanded.add(token.removeSuffix("bank"))
            }
        }
        return expanded.toList()
    }

    private fun accountMatchScore(
        account: AccountEntity,
        appName: String,
        packageName: String,
        title: String,
        text: String,
        allAccounts: List<AccountEntity>,
        excludeAccount: AccountEntity?
    ): Int {
        if (excludeAccount != null && account.id == excludeAccount.id) return 0
        val bodyLower = "$title $text".lowercase()
        val appLower = "$appName $packageName".lowercase()
        val excludeMentionsApp = excludeAccount != null &&
            appLower.isNotBlank() &&
            accountMatchScore(excludeAccount, appName, packageName, "", "", emptyList(), null) > 0
        val searchable = if (excludeAccount == null || !excludeMentionsApp) {
            "$appLower $bodyLower".trim()
        } else {
            bodyLower
        }
        if (searchable.isBlank()) return 0

        val last4 = normalizeCardLast4(account.cardLast4)
        if (last4 != null && Regex("(?<!\\d)$last4(?!\\d)").containsMatchIn(searchable)) {
            return 100
        }

        val excludeTokens = excludeAccount?.let { distinctiveAccountTokens(it).toSet() }.orEmpty()
        val candidateTokens = distinctiveAccountTokens(account).filter { it !in excludeTokens }
        val words = searchable.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()

        val matchedTokenLen = candidateTokens
            .filter { token ->
                token in words || (token.length >= 4 && words.any { w ->
                    w.startsWith(token) || (token.length >= 5 && w.contains(token))
                })
            }
            .maxOfOrNull { it.length } ?: 0
        if (matchedTokenLen > 0) {
            val fullNameNorm = account.name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
            val fullBonus = if (excludeAccount == null && fullNameNorm.length >= 4 && searchable.contains(fullNameNorm)) {
                20
            } else {
                0
            }
            return fullBonus + matchedTokenLen
        }

        if (account.type == AccountType.CREDIT_CARD &&
            allAccounts.count { it.type == AccountType.CREDIT_CARD } == 1 &&
            CREDIT_CARD_PAYMENT_PHRASES.any { bodyLower.contains(it) }
        ) {
            return 10
        }
        return 0
    }

    fun isAccountMentionedInNotification(
        account: AccountEntity,
        appName: String,
        packageName: String,
        title: String,
        text: String,
        allAccounts: List<AccountEntity> = emptyList(),
        excludeAccount: AccountEntity? = null
    ): Boolean {
        return accountMatchScore(
            account = account,
            appName = appName,
            packageName = packageName,
            title = title,
            text = text,
            allAccounts = allAccounts,
            excludeAccount = excludeAccount
        ) > 0
    }

    fun findMentionedAccount(
        accounts: List<AccountEntity>,
        appName: String,
        packageName: String,
        title: String,
        text: String,
        excludeAccount: AccountEntity? = null
    ): AccountEntity? {
        return accounts
            .filter { it.id != excludeAccount?.id }
            .map { acc ->
                acc to accountMatchScore(
                    account = acc,
                    appName = appName,
                    packageName = packageName,
                    title = title,
                    text = text,
                    allAccounts = accounts,
                    excludeAccount = excludeAccount
                )
            }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    fun isLikelyOutgoingTransfer(
        type: TransactionType,
        rawText: String,
        merchant: String,
        note: String,
        counterAccount: AccountEntity? = null
    ): Boolean {
        if (type == TransactionType.TRANSFER) return true
        val combined = "$rawText $merchant $note".lowercase()
        if (OUTGOING_TRANSFER_PHRASES.any { combined.contains(it) }) return true
        if (counterAccount != null &&
            isAccountMentionedInNotification(
                account = counterAccount,
                appName = "",
                packageName = "",
                title = merchant,
                text = "$rawText $note"
            )
        ) {
            return true
        }
        return false
    }

    fun findTransferCounterpart(
        existingCandidates: List<Pair<TransactionEntity, String>>,
        incomingType: TransactionType,
        incomingAccountId: Long?,
        incomingToAccountId: Long?,
        incomingAmount: Double,
        incomingCurrency: String,
        incomingTimestamp: Long,
        incomingText: String,
        incomingMerchant: String,
        incomingNote: String,
        accountsById: Map<Long, AccountEntity>,
        windowMillis: Long = TRANSFER_PAIRING_WINDOW_MILLIS
    ): TransferPairMatch? {
        val allAccounts = accountsById.values.toList()
        val eligible = existingCandidates
            .filter { (tx, _) ->
                !tx.isDeleted &&
                    tx.splitBillId == null &&
                    tx.reimbursedAmount <= 0.005 &&
                    abs(tx.amount - incomingAmount) < 0.005 &&
                    tx.currency.equals(incomingCurrency, ignoreCase = true) &&
                    abs(tx.timestamp - incomingTimestamp) <= windowMillis
            }
            .sortedBy { (tx, _) -> abs(tx.timestamp - incomingTimestamp) }

        val incomingCombined = "$incomingText $incomingMerchant $incomingNote".lowercase()
        val incomingHasTopUp = TOP_UP_PHRASES.any { incomingCombined.contains(it) }

        for ((existing, existingText) in eligible) {
            when {
                existing.type == TransactionType.TRANSFER && incomingType == TransactionType.INCOME -> {
                    if (incomingAccountId == null || incomingAccountId == existing.accountId) continue
                    val existingDest = existing.toAccountId?.let { accountsById[it] }
                    val existingSource = accountsById[existing.accountId]
                    val existingDestVerified = existingDest != null && isAccountMentionedInNotification(
                        account = existingDest,
                        appName = "",
                        packageName = "",
                        title = existing.merchant,
                        text = "$existingText ${existing.note}",
                        allAccounts = allAccounts,
                        excludeAccount = existingSource
                    )
                    if (existing.toAccountId == null ||
                        existing.toAccountId == incomingAccountId ||
                        existing.needsReview ||
                        !existingDestVerified
                    ) {
                        val needsUpdate = existing.toAccountId != incomingAccountId || existing.needsReview
                        return TransferPairMatch(
                            existing = existing,
                            sourceAccountId = existing.accountId,
                            destAccountId = incomingAccountId,
                            shouldUpdateExisting = needsUpdate
                        )
                    }
                }

                existing.type == TransactionType.EXPENSE && incomingType == TransactionType.INCOME -> {
                    if (incomingAccountId == null || incomingAccountId == existing.accountId) continue
                    val existingSource = accountsById[existing.accountId]
                    val incomingDest = accountsById[incomingAccountId]
                    val incomingMentionsSource = existingSource != null && isAccountMentionedInNotification(
                        account = existingSource,
                        appName = "",
                        packageName = "",
                        title = incomingMerchant,
                        text = "$incomingText $incomingNote",
                        allAccounts = allAccounts,
                        excludeAccount = incomingDest
                    )
                    val outgoingSignal = isLikelyOutgoingTransfer(
                        type = existing.type,
                        rawText = existingText,
                        merchant = existing.merchant,
                        note = existing.note,
                        counterAccount = incomingDest
                    ) || incomingMentionsSource || incomingHasTopUp
                    if (outgoingSignal) {
                        return TransferPairMatch(
                            existing = existing,
                            sourceAccountId = existing.accountId,
                            destAccountId = incomingAccountId,
                            shouldUpdateExisting = true
                        )
                    }
                }

                existing.type == TransactionType.INCOME &&
                    (incomingType == TransactionType.TRANSFER || incomingType == TransactionType.EXPENSE) -> {
                    if (incomingAccountId == null || incomingAccountId == existing.accountId) continue
                    val existingDest = accountsById[existing.accountId]
                    val incomingSource = accountsById[incomingAccountId]
                    if (incomingType == TransactionType.TRANSFER) {
                        val incomingToAcc = incomingToAccountId?.let { accountsById[it] }
                        val incomingToVerified = incomingToAcc != null && isAccountMentionedInNotification(
                            account = incomingToAcc,
                            appName = "",
                            packageName = "",
                            title = incomingMerchant,
                            text = "$incomingText $incomingNote",
                            allAccounts = allAccounts,
                            excludeAccount = incomingSource
                        )
                        if (incomingToAccountId == null ||
                            incomingToAccountId == existing.accountId ||
                            !incomingToVerified
                        ) {
                            return TransferPairMatch(
                                existing = existing,
                                sourceAccountId = incomingAccountId,
                                destAccountId = existing.accountId,
                                shouldUpdateExisting = true
                            )
                        }
                    } else {
                        val existingMentionsSource = incomingSource != null && isAccountMentionedInNotification(
                            account = incomingSource,
                            appName = "",
                            packageName = "",
                            title = existing.merchant,
                            text = "$existingText ${existing.note}",
                            allAccounts = allAccounts,
                            excludeAccount = existingDest
                        )
                        val existingHasTopUp = TOP_UP_PHRASES.any {
                            "$existingText ${existing.merchant} ${existing.note}".lowercase().contains(it)
                        }
                        val outgoingSignal = isLikelyOutgoingTransfer(
                            type = incomingType,
                            rawText = incomingText,
                            merchant = incomingMerchant,
                            note = incomingNote,
                            counterAccount = existingDest
                        ) || existingMentionsSource || existingHasTopUp
                        if (outgoingSignal) {
                            return TransferPairMatch(
                                existing = existing,
                                sourceAccountId = incomingAccountId,
                                destAccountId = existing.accountId,
                                shouldUpdateExisting = true
                            )
                        }
                    }
                }

                existing.type == TransactionType.TRANSFER && incomingType == TransactionType.TRANSFER -> {
                    val dst = incomingToAccountId ?: existing.toAccountId
                    if (incomingAccountId == null || incomingAccountId == existing.accountId) {
                        if (dst != null && dst != existing.accountId &&
                            (existing.toAccountId == null || existing.needsReview || existing.accountId == existing.toAccountId)
                        ) {
                            return TransferPairMatch(
                                existing = existing,
                                sourceAccountId = existing.accountId,
                                destAccountId = dst,
                                shouldUpdateExisting = existing.toAccountId != dst || existing.needsReview
                            )
                        }
                        if (existing.toAccountId != null && (incomingToAccountId == null || incomingToAccountId == existing.toAccountId)) {
                            return TransferPairMatch(
                                existing = existing,
                                sourceAccountId = existing.accountId,
                                destAccountId = existing.toAccountId,
                                shouldUpdateExisting = false
                            )
                        }
                    }
                }
            }
        }
        return null
    }
}
