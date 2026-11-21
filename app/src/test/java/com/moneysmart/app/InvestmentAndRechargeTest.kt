package com.moneysmart.app

import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.data.remote.MarketPriceApiClient
import com.moneysmart.app.data.repository.LedgerCalculations
import org.junit.Assert.assertEquals
import org.junit.Test

class InvestmentAndRechargeTest {

    @Test
    fun parseYahooChartQuoteExtractsPriceAndCurrency() {
        val sampleJson = """
            {
              "chart": {
                "result": [
                  {
                    "meta": {
                      "currency": "SGD",
                      "symbol": "ES3.SI",
                      "regularMarketPrice": 3.68
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val quote = MarketPriceApiClient.parseYahooChartQuote(sampleJson, "ES3.SI")
        assertEquals("ES3.SI", quote.symbol)
        assertEquals(3.68, quote.price, 0.001)
        assertEquals("SGD", quote.currency)
    }

    @Test
    fun autoRechargeShortfallAndBalanceCalibrationComputeCorrectly() {
        assertEquals(0.0, LedgerCalculations.computeAutoRechargeShortfall(5.0, 0.0), 0.001)
        assertEquals(0.0, LedgerCalculations.computeAutoRechargeShortfall(0.0, 0.0), 0.001)
        assertEquals(10.90, LedgerCalculations.computeAutoRechargeShortfall(-10.90, 0.0), 0.001)

        val payLah = AccountEntity(
            id = 9L,
            name = "PayLah",
            type = AccountType.E_WALLET,
            currency = "SGD",
            initialBalance = 0.0
        )
        val txs = listOf(
            TransactionEntity(
                id = 1L,
                type = TransactionType.EXPENSE,
                amount = 50.0,
                currency = "SGD",
                baseAmount = 50.0,
                accountId = 9L
            ),
            TransactionEntity(
                id = 2L,
                type = TransactionType.INCOME,
                amount = 20.0,
                currency = "SGD",
                baseAmount = 20.0,
                accountId = 9L
            )
        )
        // Net tx delta is -30.0. To calibrate current balance to -10.90, initialBalance must be 19.10.
        val calibrated = LedgerCalculations.computeCalibratedInitialBalance(
            account = payLah,
            transactions = txs,
            targetCurrentBalance = -10.90,
            baseCurrency = "SGD"
        )
        assertEquals(19.10, calibrated, 0.001)
        assertEquals(
            -10.90,
            LedgerCalculations.computeAccountBalance(payLah.copy(initialBalance = calibrated), txs, "SGD"),
            0.001
        )
    }
}
