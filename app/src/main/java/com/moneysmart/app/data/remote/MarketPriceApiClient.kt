package com.moneysmart.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class MarketQuote(
    val symbol: String,
    val price: Double,
    val currency: String
)

class MarketPriceApiClient(
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    suspend fun fetchQuote(userSymbol: String): Result<MarketQuote> = withContext(Dispatchers.IO) {
        val clean = userSymbol.trim().uppercase()
        if (clean.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Market ticker is empty."))
        }

        val isGoldPerGram = clean == GOLD_GRAM_SYMBOL || clean == "XAU_G" || clean == "GOLD_GRAM"
        val yahooSymbol = if (isGoldPerGram) "GC=F" else clean
        val encoded = URLEncoder.encode(yahooSymbol, "UTF-8")
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$encoded?interval=1d&range=1d"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Market quote error HTTP ${response.code} for $clean")
                }
                val body = response.body?.string().orEmpty()
                val rawQuote = parseYahooChartQuote(body, yahooSymbol)
                if (isGoldPerGram) {
                    rawQuote.copy(
                        symbol = clean,
                        price = rawQuote.price / TROY_OUNCE_IN_GRAMS
                    )
                } else {
                    rawQuote.copy(symbol = clean)
                }
            }
        }
    }

    companion object {
        const val GOLD_GRAM_SYMBOL = "XAU_GRAM"
        const val TROY_OUNCE_IN_GRAMS = 31.1034768

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        fun parseYahooChartQuote(body: String, symbol: String): MarketQuote {
            val root = json.parseToJsonElement(body).jsonObject
            val chart = root["chart"]?.jsonObject ?: error("Missing chart object")
            val results = chart["result"]?.jsonArray
            val first = results?.firstOrNull()?.jsonObject
                ?: error("No market data found for $symbol")
            val meta = first["meta"]?.jsonObject ?: error("Missing quote metadata for $symbol")

            val price = meta["regularMarketPrice"]?.jsonPrimitive?.doubleOrNull
                ?: meta["chartPreviousClose"]?.jsonPrimitive?.doubleOrNull
                ?: meta["previousClose"]?.jsonPrimitive?.doubleOrNull
                ?: error("No market price available for $symbol")

            val currency = meta["currency"]?.jsonPrimitive?.content
                ?.trim()
                ?.uppercase()
                ?.takeIf { it.length == 3 }
                ?: "USD"

            return MarketQuote(
                symbol = symbol,
                price = price,
                currency = currency
            )
        }
    }
}
