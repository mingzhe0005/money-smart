package com.moneysmart.app

import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.CategoryType
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.data.remote.GeminiApiClient
import com.moneysmart.app.data.repository.LedgerCalculations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditCardTest {

    @Test
    fun `credit card owed available and utilization calculations handle normal settled and overlimit states`() {
        assertEquals(145.60, LedgerCalculations.computeCreditCardOwed(-145.60), 0.001)
        assertEquals(0.0, LedgerCalculations.computeCreditCardOwed(0.0), 0.001)
        assertEquals(0.0, LedgerCalculations.computeCreditCardOwed(25.0), 0.001)

        assertEquals(354.40, LedgerCalculations.computeCreditCardAvailable(-145.60, 500.0)!!, 0.001)
        assertEquals(0.0, LedgerCalculations.computeCreditCardAvailable(-650.0, 500.0)!!, 0.001)
        assertNull(LedgerCalculations.computeCreditCardAvailable(-145.60, null))
        assertNull(LedgerCalculations.computeCreditCardAvailable(-145.60, 0.0))

        assertEquals(0.2912f, LedgerCalculations.computeCreditCardUtilization(-145.60, 500.0)!!, 0.001f)
        assertEquals(1.30f, LedgerCalculations.computeCreditCardUtilization(-650.0, 500.0)!!, 0.001f)
        assertNull(LedgerCalculations.computeCreditCardUtilization(-145.60, null))

        assertEquals("4821", LedgerCalculations.normalizeCardLast4("4821"))
        assertEquals("0624", LedgerCalculations.normalizeCardLast4("xx-0624"))
        assertNull(LedgerCalculations.normalizeCardLast4("123"))
        assertNull(LedgerCalculations.normalizeCardLast4(null))
    }

    @Test
    fun `paying credit card bill via transfer clears owed debt without double counting expenses`() {
        val bank = AccountEntity(
            id = 1L,
            name = "Main Bank",
            type = AccountType.BANK,
            currency = "SGD",
            initialBalance = 1000.0
        )
        val card = AccountEntity(
            id = 2L,
            name = "Everyday Card",
            type = AccountType.CREDIT_CARD,
            currency = "SGD",
            initialBalance = -50.0,
            creditLimit = 1000.0,
            cardLast4 = "4821",
            defaultPaymentAccountId = 1L
        )
        val txs = listOf(
            TransactionEntity(
                id = 10L,
                type = TransactionType.EXPENSE,
                amount = 120.0,
                currency = "SGD",
                baseAmount = 120.0,
                accountId = 2L
            ),
            TransactionEntity(
                id = 11L,
                type = TransactionType.TRANSFER,
                amount = 170.0,
                currency = "SGD",
                baseAmount = 170.0,
                accountId = 1L,
                toAccountId = 2L
            )
        )

        val cardBalance = LedgerCalculations.computeAccountBalance(card, txs, "SGD")
        val bankBalance = LedgerCalculations.computeAccountBalance(bank, txs, "SGD")
        val totalExpenses = LedgerCalculations.effectiveTotalExpensesBase(txs)

        assertEquals(0.0, LedgerCalculations.computeCreditCardOwed(cardBalance), 0.001)
        assertEquals(830.0, bankBalance, 0.001)
        assertEquals(120.0, totalExpenses, 0.001)
    }

    @Test
    fun `gemini prompt includes card last4 and credit card bill transfer rule`() {
        val card = AccountEntity(
            id = 2L,
            name = "Everyday Card",
            type = AccountType.CREDIT_CARD,
            currency = "SGD",
            cardLast4 = "4821"
        )
        val bank = AccountEntity(
            id = 1L,
            name = "Main Bank",
            type = AccountType.BANK,
            currency = "SGD"
        )
        val categories = listOf(
            CategoryEntity(id = 1L, name = "Food", type = CategoryType.EXPENSE)
        )

        val prompt = GeminiApiClient.buildPrompt(
            appName = "Bank App",
            packageName = "com.example.bank",
            title = "Card Alert",
            text = "Charged SGD 18.50 on card ending 4821 at Coffee Shop",
            baseCurrency = "SGD",
            accounts = listOf(bank, card),
            categories = categories
        )

        assertTrue(prompt.contains("last4=\"4821\""))
        assertTrue(prompt.contains("Credit card bill payments"))
        assertTrue(prompt.contains("toAccountId=credit card"))
    }
}
