package com.moneysmart.app.data.remote

import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class AiParsedNotification(
    val isFinancialTransaction: Boolean = false,
    val classification: String = "IGNORE",
    val amount: Double? = null,
    val currency: String? = null,
    val merchant: String? = null,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val note: String? = null,
    val confidence: Float = 0.0f,
    val reason: String? = null
)

data class ProbedGeminiModel(
    val name: String,
    val isTemporarilyBusy: Boolean = false
)

enum class ProbeStatus {
    USABLE,
    BUSY,
    UNAVAILABLE
}

data class NotificationAnalysisResult(
    val parsed: AiParsedNotification,
    val rawJson: String,
    val usedModel: String,
    val switchedFromUnavailable: Boolean = false
)

class GeminiRateLimitException(message: String) : Exception(message)
class GeminiTransientException(message: String) : Exception(message)
class GeminiAuthException(message: String) : Exception(message)
class GeminiModelUnavailableException(message: String) : Exception(message)

class GeminiApiClient(
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    suspend fun analyzeNotification(
        apiKey: String,
        model: String,
        appName: String,
        packageName: String,
        title: String,
        text: String,
        baseCurrency: String,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        learnedHabitsPrompt: String = ""
    ): Result<Pair<AiParsedNotification, String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(GeminiAuthException("Gemini API key is not configured."))
        }

        val prompt = buildPrompt(
            appName = appName,
            packageName = packageName,
            title = title,
            text = text,
            baseCurrency = baseCurrency,
            accounts = accounts,
            categories = categories,
            learnedHabitsPrompt = learnedHabitsPrompt
        )

        val requestPayload = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
            })
        }.toString()

        val cleanModel = model.trim().removePrefix("models/").ifBlank { "gemini-3.8-flash" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=${apiKey.trim()}"

        val request = Request.Builder()
            .url(url)
            .post(requestPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when (response.code) {
                    in 200..299 -> {
                        val rawCandidateText = extractCandidateText(body)
                        val parsed = parseNotificationResponse(rawCandidateText)
                        Result.success(parsed to rawCandidateText)
                    }
                    400, 404 -> {
                        val errMsg = extractErrorMessage(body) ?: "Model error (${response.code})"
                        if (response.code == 404 || isModelUnavailableMessage(errMsg)) {
                            Result.failure(GeminiModelUnavailableException(errMsg))
                        } else {
                            Result.failure(GeminiAuthException(errMsg))
                        }
                    }
                    401, 403 -> {
                        val errMsg = extractErrorMessage(body) ?: "Invalid API key or request (${response.code})"
                        if (isModelUnavailableMessage(errMsg)) {
                            Result.failure(GeminiModelUnavailableException(errMsg))
                        } else {
                            Result.failure(GeminiAuthException(errMsg))
                        }
                    }
                    429 -> {
                        val errMsg = extractErrorMessage(body) ?: "Gemini free-tier rate limit reached (429)"
                        Result.failure(GeminiRateLimitException(errMsg))
                    }
                    in 500..599 -> {
                        val errMsg = extractErrorMessage(body) ?: "Gemini server error (${response.code})"
                        Result.failure(GeminiTransientException(errMsg))
                    }
                    else -> {
                        val errMsg = extractErrorMessage(body) ?: "Unexpected HTTP ${response.code}"
                        Result.failure(GeminiTransientException(errMsg))
                    }
                }
            }
        } catch (e: IOException) {
            Result.failure(GeminiTransientException("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(GeminiAuthException("Please enter your Gemini API key first."))
        }
        val cleanModel = model.trim().removePrefix("models/").ifBlank { "gemini-3.8-flash" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=${apiKey.trim()}"
        val payload = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", "Reply with JSON: {\"status\":\"ok\"}")
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.0)
                put("responseMimeType", "application/json")
            })
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val text = extractCandidateText(body)
                    Result.success("Connected to $cleanModel ($text)")
                } else {
                    val msg = extractErrorMessage(body) ?: "HTTP ${response.code}"
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeNotificationWithFallback(
        apiKey: String,
        primaryModel: String,
        appName: String,
        packageName: String,
        title: String,
        text: String,
        baseCurrency: String,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        learnedHabitsPrompt: String = ""
    ): Result<NotificationAnalysisResult> {
        val cleanPrimary = primaryModel.trim().removePrefix("models/").ifBlank { DEFAULT_MODEL }
        val primaryResult = analyzeNotification(
            apiKey = apiKey,
            model = cleanPrimary,
            appName = appName,
            packageName = packageName,
            title = title,
            text = text,
            baseCurrency = baseCurrency,
            accounts = accounts,
            categories = categories,
            learnedHabitsPrompt = learnedHabitsPrompt
        )
        if (primaryResult.isSuccess) {
            val (parsed, rawJson) = primaryResult.getOrThrow()
            return Result.success(
                NotificationAnalysisResult(
                    parsed = parsed,
                    rawJson = rawJson,
                    usedModel = cleanPrimary,
                    switchedFromUnavailable = false
                )
            )
        }

        val primaryErr = primaryResult.exceptionOrNull()
        if (primaryErr is GeminiAuthException) {
            return Result.failure(primaryErr)
        }

        val candidates = listAvailableModels(apiKey).getOrNull()
            .orEmpty()
            .filter { !it.equals(cleanPrimary, ignoreCase = true) }
            .take(4)

        for (candidate in candidates) {
            val fallbackRes = analyzeNotification(
                apiKey = apiKey,
                model = candidate,
                appName = appName,
                packageName = packageName,
                title = title,
                text = text,
                baseCurrency = baseCurrency,
                accounts = accounts,
                categories = categories,
                learnedHabitsPrompt = learnedHabitsPrompt
            )
            if (fallbackRes.isSuccess) {
                val (parsed, rawJson) = fallbackRes.getOrThrow()
                return Result.success(
                    NotificationAnalysisResult(
                        parsed = parsed,
                        rawJson = rawJson,
                        usedModel = candidate,
                        switchedFromUnavailable = primaryErr is GeminiModelUnavailableException
                    )
                )
            }
            if (fallbackRes.exceptionOrNull() is GeminiAuthException) {
                break
            }
        }

        return Result.failure(primaryErr ?: Exception("All candidate Gemini models failed."))
    }

    suspend fun listAvailableModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(GeminiAuthException("Please enter your Gemini API key first."))
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey.trim()}"
        val request = Request.Builder().url(url).get().build()
        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    Result.success(parseModelsListResponse(body))
                } else {
                    val msg = extractErrorMessage(body) ?: "HTTP ${response.code}"
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listUsableFreeModels(apiKey: String): Result<List<ProbedGeminiModel>> = withContext(Dispatchers.IO) {
        val candidatesResult = listAvailableModels(apiKey)
        if (candidatesResult.isFailure) {
            return@withContext Result.failure(candidatesResult.exceptionOrNull()!!)
        }
        val candidates = candidatesResult.getOrThrow().take(12)
        if (candidates.isEmpty()) {
            return@withContext Result.success(emptyList())
        }

        val semaphore = Semaphore(3)
        val probed = coroutineScope {
            candidates.map { modelName ->
                async {
                    semaphore.withPermit {
                        val status = probeModelUsability(apiKey, modelName)
                        when (status) {
                            ProbeStatus.USABLE -> ProbedGeminiModel(modelName, isTemporarilyBusy = false)
                            ProbeStatus.BUSY -> ProbedGeminiModel(modelName, isTemporarilyBusy = true)
                            ProbeStatus.UNAVAILABLE -> null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val sorted = probed.sortedWith(
            compareBy<ProbedGeminiModel> { it.isTemporarilyBusy }
                .thenByDescending { it.name.contains("flash", ignoreCase = true) }
                .thenByDescending { it.name }
        )
        Result.success(sorted)
    }

    private fun probeModelUsability(apiKey: String, model: String): ProbeStatus {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${apiKey.trim()}"
        val payload = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", "Reply {\"ok\":true}")
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.0)
                put("maxOutputTokens", 16)
                put("responseMimeType", "application/json")
            })
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                classifyProbeResult(response.code, extractErrorMessage(body))
            }
        } catch (_: IOException) {
            ProbeStatus.BUSY
        } catch (_: Exception) {
            ProbeStatus.UNAVAILABLE
        }
    }

    companion object {
        private val lenientJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        fun buildPrompt(
            appName: String,
            packageName: String,
            title: String,
            text: String,
            baseCurrency: String,
            accounts: List<AccountEntity>,
            categories: List<CategoryEntity>,
            learnedHabitsPrompt: String = ""
        ): String {
            val accountLines = if (accounts.isEmpty()) {
                "None"
            } else {
                accounts.joinToString("\n") { acc ->
                    val last4Part = acc.cardLast4?.takeIf { it.isNotBlank() }?.let { ", last4=\"$it\"" } ?: ""
                    "- id=${acc.id}, name=\"${acc.name}\", type=${acc.type.name}, currency=${acc.currency}$last4Part"
                }
            }

            val parentNames = categories.associateBy { it.id }
            val categoryLines = if (categories.isEmpty()) {
                "None"
            } else {
                categories.joinToString("\n") { cat ->
                    val parentLabel = cat.parentId?.let { pid -> parentNames[pid]?.name }?.let { " (subcategory of $it)" } ?: ""
                    "- id=${cat.id}, name=\"${cat.name}\"$parentLabel, type=${cat.type.name}"
                }
            }

            val habitsBlock = if (learnedHabitsPrompt.isBlank()) {
                ""
            } else {
                """
                
                Learned User Spending Habits (strongly prefer matching these past habits for recurring merchants or similar notes):
                $learnedHabitsPrompt
                """.trimIndent()
            }

            return """
                You are a financial notification parser for a personal expense tracker called Money Smart.
                Analyze the Android notification below and decide whether it represents a completed financial transaction (expense/purchase, income/deposit/refund, or transfer between accounts).

                CRITICAL RULES:
                1. Set isFinancialTransaction=false and classification="IGNORE" for:
                   - OTPs, 2FA codes, verification messages, login alerts
                   - Promotional ads, cashback marketing offers, discount coupons
                   - Declined, failed, or cancelled payment attempts
                   - General balance reminders or statements with no transaction event
                2. Otherwise, set isFinancialTransaction=true and classification to one of: "EXPENSE", "INCOME", "TRANSFER".
                   - Bank cashbacks, rebates, refunds, and personal transfers paying you back (e.g., friends or family repaying money you covered) should use classification="INCOME" with the "Reimbursement" category.
                   - Credit card bill payments / repayments from a bank account to a credit card should use classification="TRANSFER" (accountId=source bank, toAccountId=credit card).
                3. Infer accountId (and toAccountId if TRANSFER) ONLY from the User Accounts list by matching bank name, card type, card last 4 digits (last4), wallet name, or app name mentioned in the notification. Use null if unclear.
                4. Infer categoryId ONLY from the User Categories list (prefer matching a subcategory if appropriate; ensure category type matches EXPENSE/INCOME; use null for TRANSFER or if unsure). Use the Learned User Spending Habits below whenever a merchant/note matches the user's history.
                5. Extract positive numeric amount (no currency symbol), 3-letter ISO currency code (e.g. USD, SGD, MYR, EUR, GBP, JPY; if currency symbol is ambiguous like "${'$'}", infer from the notification or default to "$baseCurrency"), merchant/counterparty name, and a brief note.
                6. Output confidence from 0.0 to 1.0 reflecting how certain you are about the classification, amount, and account/category match.

                Return strictly a JSON object matching this exact schema:
                {
                  "isFinancialTransaction": boolean,
                  "classification": "EXPENSE" | "INCOME" | "TRANSFER" | "IGNORE",
                  "amount": number | null,
                  "currency": string | null,
                  "merchant": string | null,
                  "accountId": number | null,
                  "toAccountId": number | null,
                  "categoryId": number | null,
                  "note": string | null,
                  "confidence": number,
                  "reason": string
                }

                User Base Currency: $baseCurrency

                User Accounts:
                $accountLines

                User Categories:
                $categoryLines
                $habitsBlock

                Notification:
                - Source App: $appName ($packageName)
                - Title: $title
                - Text: $text
            """.trimIndent()
        }

        fun extractCandidateText(apiResponseBody: String): String {
            val root = lenientJson.parseToJsonElement(apiResponseBody).jsonObject
            val candidates = root["candidates"]?.jsonArray
                ?: throw IllegalStateException("No candidates in Gemini response")
            val firstCandidate = candidates.firstOrNull()?.jsonObject
                ?: throw IllegalStateException("Empty candidates list in Gemini response")
            val content = firstCandidate["content"]?.jsonObject
                ?: throw IllegalStateException("No content in Gemini candidate")
            val parts = content["parts"]?.jsonArray
                ?: throw IllegalStateException("No parts in Gemini content")
            return parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                .joinToString("\n")
                .trim()
        }

        fun parseNotificationResponse(rawText: String): AiParsedNotification {
            val cleaned = stripMarkdownCodeFences(rawText).trim()
            val jsonStart = cleaned.indexOf('{')
            val jsonEnd = cleaned.lastIndexOf('}')
            val jsonSlice = if (jsonStart >= 0 && jsonEnd > jsonStart) {
                cleaned.substring(jsonStart, jsonEnd + 1)
            } else {
                cleaned
            }
            val parsed = lenientJson.decodeFromString(AiParsedNotification.serializer(), jsonSlice)
            val normalizedClass = parsed.classification.trim().uppercase()
            return parsed.copy(
                classification = normalizedClass,
                currency = parsed.currency?.trim()?.uppercase()?.takeIf { it.length == 3 },
                merchant = parsed.merchant?.trim()?.takeIf { it.isNotEmpty() },
                note = parsed.note?.trim()?.takeIf { it.isNotEmpty() },
                confidence = parsed.confidence.coerceIn(0f, 1f)
            )
        }

        fun stripMarkdownCodeFences(text: String): String {
            var s = text.trim()
            if (s.startsWith("```")) {
                val firstNewline = s.indexOf('\n')
                s = if (firstNewline != -1) s.substring(firstNewline + 1) else s.removePrefix("```json").removePrefix("```")
            }
            if (s.endsWith("```")) {
                s = s.removeSuffix("```")
            }
            return s.trim()
        }

        private fun extractErrorMessage(body: String): String? {
            return runCatching {
                val obj = lenientJson.parseToJsonElement(body).jsonObject
                obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
        }

        const val DEFAULT_MODEL = "gemini-3.8-flash"

        private val excludedModelTokens = listOf(
            "image", "imagen", "veo", "tts", "audio", "video", "live",
            "embedding", "aqa", "robotics", "computer-use", "deep-research",
            "learnlm", "customtools", "tuned", "nano-banana", "hydrogen"
        )

        fun isModelUnavailableMessage(message: String?): Boolean {
            if (message.isNullOrBlank()) return false
            val lower = message.lowercase()
            return lower.contains("no longer available") ||
                lower.contains("not found") ||
                lower.contains("not supported") ||
                lower.contains("deprecated") ||
                lower.contains("retired") ||
                lower.contains("does not exist")
        }

        fun classifyProbeResult(httpCode: Int, errorMessage: String?): ProbeStatus {
            return when (httpCode) {
                in 200..299 -> ProbeStatus.USABLE
                503 -> ProbeStatus.BUSY
                in 500..599 -> {
                    val lower = errorMessage.orEmpty().lowercase()
                    if (lower.contains("demand") || lower.contains("overload") || lower.contains("unavailable")) {
                        ProbeStatus.BUSY
                    } else {
                        ProbeStatus.UNAVAILABLE
                    }
                }
                else -> ProbeStatus.UNAVAILABLE
            }
        }

        fun parseModelsListResponse(body: String): List<String> {
            val root = lenientJson.parseToJsonElement(body).jsonObject
            val models = root["models"]?.jsonArray ?: return emptyList()
            return models.mapNotNull { elem ->
                val obj = elem.jsonObject
                val rawName = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val cleanName = rawName.removePrefix("models/").trim()
                val lower = cleanName.lowercase()
                if (!lower.startsWith("gemini-")) return@mapNotNull null
                if (excludedModelTokens.any { token -> lower.contains(token) }) return@mapNotNull null
                val methods = obj["supportedGenerationMethods"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?: emptyList()
                if ("generateContent" !in methods) return@mapNotNull null
                cleanName
            }.distinct()
                .sortedWith(
                    compareByDescending<String> { it.contains("flash", ignoreCase = true) }
                        .thenByDescending { it }
                )
        }
    }
}
