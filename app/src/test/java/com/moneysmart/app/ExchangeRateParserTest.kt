package com.moneysmart.app

import com.moneysmart.app.data.remote.ExchangeRateApiClient
import org.junit.Assert.assertEquals
import org.junit.Test

class ExchangeRateParserTest {

    @Test
    fun parseOpenErApiRates() {
        val body = """
            {
              "result": "success",
              "base_code": "USD",
              "rates": {
                "USD": 1,
                "SGD": 1.34,
                "EUR": 0.92
              }
            }
        """.trimIndent()
        val rates = ExchangeRateApiClient.parseOpenErApiResponse(body, "USD")
        assertEquals(1.0, rates["USD"]!!, 0.0001)
        assertEquals(1.34, rates["SGD"]!!, 0.0001)
        assertEquals(0.92, rates["EUR"]!!, 0.0001)
    }

    @Test
    fun parseFrankfurterFallbackRates() {
        val body = """
            {
              "amount": 1.0,
              "base": "USD",
              "date": "2026-11-20",
              "rates": {
                "EUR": 0.915,
                "JPY": 154.2
              }
            }
        """.trimIndent()
        val rates = ExchangeRateApiClient.parseFrankfurterResponse(body, "USD")
        assertEquals(1.0, rates["USD"]!!, 0.0001)
        assertEquals(0.915, rates["EUR"]!!, 0.0001)
        assertEquals(154.2, rates["JPY"]!!, 0.0001)
    }
}
