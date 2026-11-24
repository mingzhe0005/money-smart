package com.moneysmart.app

import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.data.repository.LedgerCalculations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerAndDedupTest {

    @Test
    fun accountBalancesReflectExpenseIncomeTransferAndIgnoreDeleted() {
        val bank = AccountEntity(id = 1L, name = "Bank", type = AccountType.BANK, currency = "USD", initialBalance = 1000.0)
        val wallet = AccountEntity(id = 2L, name = "Wallet", type = AccountType.E_WALLET, currency = "USD", initialBalance = 50.0)

        val txs = listOf(
            TransactionEntity(id = 1, type = TransactionType.INCOME, amount = 500.0, currency = "USD", baseAmount = 500.0, accountId = 1L),
            TransactionEntity(id = 2, type = TransactionType.EXPENSE, amount = 120.0, currency = "USD", baseAmount = 120.0, accountId = 1L),
            TransactionEntity(id = 3, type = TransactionType.TRANSFER, amount = 200.0, currency = "USD", baseAmount = 200.0, accountId = 1L, toAccountId = 2L),
            TransactionEntity(id = 4, type = TransactionType.EXPENSE, amount = 30.0, currency = "USD", baseAmount = 30.0, accountId = 2L),
            TransactionEntity(id = 5, type = TransactionType.EXPENSE, amount = 999.0, currency = "USD", baseAmount = 999.0, accountId = 1L, isDeleted = true)
        )

        // Bank: 1000 + 500 - 120 - 200 = 1180.0
        assertEquals(1180.0, LedgerCalculations.computeAccountBalance(bank, txs, "USD"), 0.001)
        // Wallet: 50 + 200 - 30 = 220.0
        assertEquals(220.0, LedgerCalculations.computeAccountBalance(wallet, txs, "USD"), 0.001)
    }

    @Test
    fun multiCurrencyRateConversionComputesAccurateBaseAmount() {
        // Suppose base is USD and API reports 1 USD = 0.80 GBP
        val rateToBase = LedgerCalculations.exchangeRateToBaseFromApiRate(
            txCurrency = "GBP",
            baseCurrency = "USD",
            apiRateBaseToTx = 0.80
        )
        assertEquals(1.25, rateToBase, 0.0001)
        val baseAmount = LedgerCalculations.computeBaseAmount(80.0, rateToBase)
        assertEquals(100.0, baseAmount, 0.01)
    }

    @Test
    fun deduplicationMatchesSameAmountAndCurrencyWithinTwoMinutes() {
        val baseTime = 1_700_000_000_000L
        val existing = listOf(
            TransactionEntity(
                id = 10L,
                type = TransactionType.EXPENSE,
                amount = 19.99,
                currency = "USD",
                baseAmount = 19.99,
                accountId = 1L,
                timestamp = baseTime
            ),
            TransactionEntity(
                id = 11L,
                type = TransactionType.EXPENSE,
                amount = 45.00,
                currency = "USD",
                baseAmount = 45.00,
                accountId = 1L,
                timestamp = baseTime,
                isDeleted = true
            )
        )

        val match = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 19.99,
            currency = "usd",
            timestamp = baseTime + 60_000L
        )
        assertNotNull(match)
        assertEquals(10L, match!!.id)

        val outsideWindow = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 19.99,
            currency = "USD",
            timestamp = baseTime + 180_000L
        )
        assertNull(outsideWindow)

        val differentCurrency = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 19.99,
            currency = "EUR",
            timestamp = baseTime + 5_000L
        )
        assertNull(differentCurrency)

        val deletedNotMatched = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 45.00,
            currency = "USD",
            timestamp = baseTime + 1_000L
        )
        assertNull(deletedNotMatched)
    }

    @Test
    fun crossAppMerchantDeduplicationCatchesDelayedBankAlertAfterWalletTap() {
        val tapTime = 1_700_000_000_000L
        val existing = listOf(
            TransactionEntity(
                id = 20L,
                type = TransactionType.EXPENSE,
                amount = 6.40,
                currency = "SGD",
                baseAmount = 6.40,
                accountId = 2L,
                merchant = "PLAYMADE - JURONG POINT",
                timestamp = tapTime,
                rawNotificationId = 100L
            )
        )
        val sourcePackages = mapOf(20L to "com.samsung.android.spay")

        // OCBC alert arrives 1 minute later with truncated merchant name
        val ocbc1MinLater = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 6.40,
            currency = "SGD",
            timestamp = tapTime + 60_000L,
            type = TransactionType.EXPENSE,
            merchant = "PLAYMADE - J...",
            isDifferentSourceApp = { tx -> sourcePackages[tx.id] != "com.ocbc.mobile" }
        )
        assertNotNull(ocbc1MinLater)
        assertEquals(20L, ocbc1MinLater!!.id)

        // OCBC alert arrives 6 minutes later (outside 2-min window, inside 15-min cross-app merchant window)
        val ocbc6MinLater = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 6.40,
            currency = "SGD",
            timestamp = tapTime + 360_000L,
            type = TransactionType.EXPENSE,
            merchant = "PLAYMADE - J...",
            isDifferentSourceApp = { tx -> sourcePackages[tx.id] != "com.ocbc.mobile" }
        )
        assertNotNull(ocbc6MinLater)
        assertEquals(20L, ocbc6MinLater!!.id)

        // Same app 6 minutes later -> keep as separate transaction (e.g., buying a second drink)
        val sameAppLater = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 6.40,
            currency = "SGD",
            timestamp = tapTime + 360_000L,
            type = TransactionType.EXPENSE,
            merchant = "PLAYMADE - JURONG POINT",
            isDifferentSourceApp = { tx -> sourcePackages[tx.id] != "com.samsung.android.spay" }
        )
        assertNull(sameAppLater)

        // Different merchant from another app 6 minutes later -> keep as separate transaction
        val differentMerchant = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 6.40,
            currency = "SGD",
            timestamp = tapTime + 360_000L,
            type = TransactionType.EXPENSE,
            merchant = "7-ELEVEN",
            isDifferentSourceApp = { tx -> sourcePackages[tx.id] != "com.ocbc.mobile" }
        )
        assertNull(differentMerchant)

        // Different transaction type (e.g., INCOME refund/repayment) 30 seconds later -> keep separate
        val differentType = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 6.40,
            currency = "SGD",
            timestamp = tapTime + 30_000L,
            type = TransactionType.INCOME,
            merchant = "PLAYMADE - JURONG POINT",
            isDifferentSourceApp = { tx -> sourcePackages[tx.id] != "com.ocbc.mobile" }
        )
        assertNull(differentType)
    }

    @Test
    fun standaloneReimbursementsOffsetExpensesAndExcludeFromIncomeWithoutDoubleCountingSplits() {
        val transactions = listOf(
            // Salary = SGD 1000
            TransactionEntity(id = 1L, type = TransactionType.INCOME, amount = 1000.0, currency = "SGD", baseAmount = 1000.0, accountId = 1L),
            // Covered dinner for Dad = SGD 50
            TransactionEntity(id = 2L, type = TransactionType.EXPENSE, amount = 50.0, currency = "SGD", baseAmount = 50.0, accountId = 1L),
            // Dad transfers SGD 50 back marked as Reimbursement (unlinked to split bill)
            TransactionEntity(id = 3L, type = TransactionType.INCOME, amount = 50.0, currency = "SGD", baseAmount = 50.0, accountId = 1L, isReimbursement = true, splitBillId = null),
            // Groceries SGD 30, MariBank Cashback SGD 2.50 marked as Reimbursement
            TransactionEntity(id = 4L, type = TransactionType.EXPENSE, amount = 30.0, currency = "SGD", baseAmount = 30.0, accountId = 1L),
            TransactionEntity(id = 5L, type = TransactionType.INCOME, amount = 2.50, currency = "SGD", baseAmount = 2.50, accountId = 1L, isReimbursement = true, splitBillId = null),
            // Split bill dinner SGD 60 where friend repaid SGD 30 (already reflected in reimbursedBaseAmount)
            TransactionEntity(id = 6L, type = TransactionType.EXPENSE, amount = 60.0, currency = "SGD", baseAmount = 60.0, reimbursedAmount = 30.0, reimbursedBaseAmount = 30.0, accountId = 1L, splitBillId = 10L),
            TransactionEntity(id = 7L, type = TransactionType.INCOME, amount = 30.0, currency = "SGD", baseAmount = 30.0, accountId = 1L, isReimbursement = true, splitBillId = 10L)
        )

        val totalIncome = transactions.sumOf { LedgerCalculations.effectiveIncomeBaseAmount(it) }
        val totalExpenses = LedgerCalculations.effectiveTotalExpensesBase(transactions)

        // Only real Salary (1000.0) counts as income; reimbursements must not inflate income
        assertEquals(1000.0, totalIncome, 0.001)
        // Net expenses: (50 - 50) + (30 - 2.50) + (60 - 30) = 57.50 (split repayment #7 not subtracted twice)
        assertEquals(57.50, totalExpenses, 0.001)
    }
}
