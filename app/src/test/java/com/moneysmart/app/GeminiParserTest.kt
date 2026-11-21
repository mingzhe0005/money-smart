package com.moneysmart.app

import com.moneysmart.app.data.remote.GeminiApiClient
import com.moneysmart.app.data.remote.ProbeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiParserTest {

    @Test
    fun parseExpenseWithMarkdownCodeFence() {
        val raw = """
            ```json
            {
              "isFinancialTransaction": true,
              "classification": "expense",
              "amount": 14.80,
              "currency": "sgd",
              "merchant": "GrabFood",
              "accountId": 2,
              "toAccountId": null,
              "categoryId": 5,
              "note": "Lunch order",
              "confidence": 0.94,
              "reason": "Completed Grab payment alert"
            }
            ```
        """.trimIndent()

        val parsed = GeminiApiClient.parseNotificationResponse(raw)
        assertTrue(parsed.isFinancialTransaction)
        assertEquals("EXPENSE", parsed.classification)
        assertEquals(14.80, parsed.amount!!, 0.001)
        assertEquals("SGD", parsed.currency)
        assertEquals("GrabFood", parsed.merchant)
        assertEquals(2L, parsed.accountId)
        assertNull(parsed.toAccountId)
        assertEquals(5L, parsed.categoryId)
        assertEquals(0.94f, parsed.confidence, 0.001f)
    }

    @Test
    fun parseOtpAsIgnore() {
        val raw = """
            {
              "isFinancialTransaction": false,
              "classification": "IGNORE",
              "amount": null,
              "currency": null,
              "merchant": null,
              "accountId": null,
              "toAccountId": null,
              "categoryId": null,
              "note": null,
              "confidence": 0.99,
              "reason": "OTP verification message"
            }
        """.trimIndent()

        val parsed = GeminiApiClient.parseNotificationResponse(raw)
        assertFalse(parsed.isFinancialTransaction)
        assertEquals("IGNORE", parsed.classification)
        assertNull(parsed.amount)
    }

    @Test
    fun extractCandidateTextFromGeminiEnvelope() {
        val envelope = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\"isFinancialTransaction\":true,\"classification\":\"INCOME\",\"amount\":2500.0,\"currency\":\"USD\",\"confidence\":0.96}"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val candidateText = GeminiApiClient.extractCandidateText(envelope)
        val parsed = GeminiApiClient.parseNotificationResponse(candidateText)
        assertTrue(parsed.isFinancialTransaction)
        assertEquals("INCOME", parsed.classification)
        assertEquals(2500.0, parsed.amount!!, 0.001)
        assertEquals("USD", parsed.currency)
    }

    @Test
    fun parseModelsListFiltersNonFreeOrSpecializedModelsAndSortsFlashFirst() {
        val body = """
            {
              "models": [
                {
                  "name": "models/embedding-001",
                  "supportedGenerationMethods": ["embedContent"]
                },
                {
                  "name": "models/gemini-4-hydrogen",
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/gemini-2.5-flash-image",
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/gemini-2.5-flash-preview-tts",
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/gemini-3.8-pro",
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/gemini-3.8-flash",
                  "supportedGenerationMethods": ["generateContent", "countTokens"]
                },
                {
                  "name": "models/gemini-3.8-flash-lite",
                  "supportedGenerationMethods": ["generateContent"]
                }
              ]
            }
        """.trimIndent()

        val models = GeminiApiClient.parseModelsListResponse(body)
        assertEquals(
            listOf("gemini-3.8-flash-lite", "gemini-3.8-flash", "gemini-3.8-pro"),
            models
        )
    }

    @Test
    fun classifyProbeResultHandlesUsableBusyAndUnavailableModels() {
        assertEquals(ProbeStatus.USABLE, GeminiApiClient.classifyProbeResult(200, null))
        assertEquals(ProbeStatus.BUSY, GeminiApiClient.classifyProbeResult(503, "high demand"))
        assertEquals(ProbeStatus.UNAVAILABLE, GeminiApiClient.classifyProbeResult(404, "not found"))
        assertEquals(ProbeStatus.UNAVAILABLE, GeminiApiClient.classifyProbeResult(429, "free_tier limit: 0"))
        assertEquals(ProbeStatus.UNAVAILABLE, GeminiApiClient.classifyProbeResult(400, "no longer available"))
    }
}
