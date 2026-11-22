package com.moneysmart.app.data.repository

import com.moneysmart.app.data.local.entity.AccountEntity
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

    private val DEDUP_STOPWORDS = setOf(
        "debit", "credit", "card", "payment", "purchase", "singapore",
        "wallet", "bank", "transfer", "online", "charged",
        "frank", "ocbc", "dbs", "posb", "uob", "samsung"
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
}
