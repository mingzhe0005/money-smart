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
}
