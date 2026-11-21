package com.moneysmart.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ExchangeRateApiClient(
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    suspend fun fetchRates(baseCurrency: String): Result<Map<String, Double>> = withContext(Dispatchers.IO) {
        val base = baseCurrency.trim().uppercase().ifBlank { "USD" }

        // Primary: open.er-api.com (completely free, no API key required)
        val primaryResult = runCatching {
            val request = Request.Builder()
                .url("https://open.er-api.com/v6/latest/$base")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                parseOpenErApiResponse(body, base)
            }
        }
        if (primaryResult.isSuccess) return@withContext primaryResult

        // Fallback: api.frankfurter.app (free ECB rates, no API key required)
        val fallbackResult = runCatching {
            val request = Request.Builder()
                .url("https://api.frankfurter.app/latest?from=$base")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                parseFrankfurterResponse(body, base)
            }
        }
        fallbackResult
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        fun parseOpenErApiResponse(body: String, baseCurrency: String): Map<String, Double> {
            val root = json.parseToJsonElement(body).jsonObject
            val status = root["result"]?.jsonPrimitive?.content
            if (status != "success") {
                throw IllegalStateException("Exchange rate API returned status=$status")
            }
            val ratesObj = root["rates"]?.jsonObject ?: error("Missing rates object")
            val result = mutableMapOf<String, Double>()
            for ((code, value) in ratesObj) {
                value.jsonPrimitive.doubleOrNull?.takeIf { it > 0.0 }?.let { rate ->
                    result[code.uppercase()] = rate
                }
            }
            result[baseCurrency.uppercase()] = 1.0
            return result
        }

        fun parseFrankfurterResponse(body: String, baseCurrency: String): Map<String, Double> {
            val root = json.parseToJsonElement(body).jsonObject
            val ratesObj = root["rates"]?.jsonObject ?: error("Missing rates object")
            val result = mutableMapOf<String, Double>()
            for ((code, value) in ratesObj) {
                value.jsonPrimitive.doubleOrNull?.takeIf { it > 0.0 }?.let { rate ->
                    result[code.uppercase()] = rate
                }
            }
            result[baseCurrency.uppercase()] = 1.0
            return result
        }
    }
}
