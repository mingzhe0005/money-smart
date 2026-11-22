package com.moneysmart.app.notification

import android.content.Context
import com.moneysmart.app.data.local.SettingsDataStore
import com.moneysmart.app.data.local.entity.CategoryType
import com.moneysmart.app.data.local.entity.NotificationProcessStatus
import com.moneysmart.app.data.local.entity.RawNotificationEntity
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionSource
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.data.remote.AiParsedNotification
import com.moneysmart.app.data.remote.GeminiApiClient
import com.moneysmart.app.data.remote.GeminiRateLimitException
import com.moneysmart.app.data.remote.GeminiTransientException
import com.moneysmart.app.data.repository.HabitLearningEngine
import com.moneysmart.app.data.repository.LedgerCalculations
import com.moneysmart.app.data.repository.MoneyRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class ProcessOutcome {
    data class Recorded(val transactionId: Long, val needsReview: Boolean, val summary: String) : ProcessOutcome()
    data class Duplicate(val existingTransactionId: Long) : ProcessOutcome()
    data class Ignored(val reason: String) : ProcessOutcome()
    data class RetryableFailure(val message: String) : ProcessOutcome()
    data class PermanentFailure(val message: String) : ProcessOutcome()
}

class NotificationProcessor(
    private val context: Context,
    private val repository: MoneyRepository,
    private val settingsDataStore: SettingsDataStore,
    private val geminiApiClient: GeminiApiClient
) {
    private val recordMutex = Mutex()
    suspend fun simulateParse(
        appName: String,
        title: String,
        text: String
    ): Result<Pair<AiParsedNotification, String>> {
        val settings = settingsDataStore.getCurrentSettings()
        val accounts = repository.getActiveAccounts()
        val categories = repository.getAllCategories()
        val habitsPrompt = buildHabitsPrompt(appName, title, text, accounts, categories)
        return geminiApiClient.analyzeNotificationWithFallback(
            apiKey = settings.geminiApiKey,
            primaryModel = settings.geminiModel,
            appName = appName,
            packageName = "com.simulator.test",
            title = title,
            text = text,
            baseCurrency = settings.baseCurrency,
            accounts = accounts,
            categories = categories,
            learnedHabitsPrompt = habitsPrompt
        ).map { it.parsed to it.rawJson }
    }

    private suspend fun buildHabitsPrompt(
        appName: String,
        title: String,
        text: String,
        accounts: List<com.moneysmart.app.data.local.entity.AccountEntity>,
        categories: List<com.moneysmart.app.data.local.entity.CategoryEntity>
    ): String {
        val history = repository.getAllActiveTransactions()
        val allHabits = HabitLearningEngine.extractTopHabits(history, accounts, categories)
        val relevant = HabitLearningEngine.selectRelevantHabitsForNotification(
            habits = allHabits,
            appName = appName,
            title = title,
            text = text
        )
        return HabitLearningEngine.buildHabitPromptSection(relevant)
    }

    suspend fun processRawNotification(rawNotificationId: Long): ProcessOutcome {
        val raw = repository.getRawNotificationById(rawNotificationId)
            ?: return ProcessOutcome.PermanentFailure("Notification record #$rawNotificationId not found.")

        if (raw.status == NotificationProcessStatus.RECORDED ||
            raw.status == NotificationProcessStatus.DUPLICATE ||
            raw.status == NotificationProcessStatus.IGNORED
        ) {
            return ProcessOutcome.Ignored("Already processed (${raw.status.displayName}).")
        }

        val settings = settingsDataStore.getCurrentSettings()
        if (settings.geminiApiKey.isBlank()) {
            val msg = "Gemini API key is not configured in Settings."
            repository.updateRawNotification(
                raw.copy(status = NotificationProcessStatus.FAILED, errorMessage = msg)
            )
            return ProcessOutcome.PermanentFailure(msg)
        }

        repository.updateRawNotification(
            raw.copy(status = NotificationProcessStatus.PROCESSING, errorMessage = null)
        )

        val accounts = repository.getActiveAccounts()
        val categories = repository.getAllCategories()
        val habitsPrompt = buildHabitsPrompt(raw.appName, raw.title, raw.text, accounts, categories)

        val apiResult = geminiApiClient.analyzeNotificationWithFallback(
            apiKey = settings.geminiApiKey,
            primaryModel = settings.geminiModel,
            appName = raw.appName,
            packageName = raw.packageName,
            title = raw.title,
            text = raw.text,
            baseCurrency = settings.baseCurrency,
            accounts = accounts,
            categories = categories,
            learnedHabitsPrompt = habitsPrompt
        )

        if (apiResult.isFailure) {
            val err = apiResult.exceptionOrNull()
            val msg = err?.message ?: "Unknown AI parsing error"
            return if (err is GeminiRateLimitException || err is GeminiTransientException) {
                repository.updateRawNotification(
                    raw.copy(
                        status = NotificationProcessStatus.PENDING,
                        errorMessage = msg,
                        retryCount = raw.retryCount + 1
                    )
                )
                ProcessOutcome.RetryableFailure(msg)
            } else {
                repository.updateRawNotification(
                    raw.copy(
                        status = NotificationProcessStatus.FAILED,
                        errorMessage = msg
                    )
                )
                ProcessOutcome.PermanentFailure(msg)
            }
        }

        val analysis = apiResult.getOrThrow()
        if (analysis.switchedFromUnavailable) {
            settingsDataStore.setGeminiConfig(settings.geminiApiKey, analysis.usedModel)
        }
        val parsed = analysis.parsed
        val rawJson = analysis.rawJson

        if (!parsed.isFinancialTransaction ||
            parsed.classification == "IGNORE" ||
            parsed.amount == null ||
            parsed.amount <= 0.0
        ) {
            val reason = parsed.reason ?: "Not a completed financial transaction"
            repository.updateRawNotification(
                raw.copy(
                    status = NotificationProcessStatus.IGNORED,
                    aiResponseJson = rawJson,
                    errorMessage = reason
                )
            )
            return ProcessOutcome.Ignored(reason)
        }

        val txType = when (parsed.classification) {
            "INCOME" -> TransactionType.INCOME
            "TRANSFER" -> TransactionType.TRANSFER
            else -> TransactionType.EXPENSE
        }

        val rawCurrency = parsed.currency?.trim()?.uppercase().orEmpty()
        val currency = when {
            rawCurrency == "S$" || rawCurrency == "SG$" -> "SGD"
            rawCurrency.length == 3 && rawCurrency.all { it.isLetter() } -> rawCurrency
            else -> settings.baseCurrency
        }
        val merchantName = parsed.merchant ?: raw.appName

        val (savedTx, accountName, categoryName) = recordMutex.withLock {
            val duplicate = repository.findPotentialDuplicate(
                amount = parsed.amount,
                currency = currency,
                timestamp = raw.postedAt,
                type = txType,
                merchant = merchantName,
                sourcePackageName = raw.packageName
            )
            if (duplicate != null) {
                repository.updateRawNotification(
                    raw.copy(
                        status = NotificationProcessStatus.DUPLICATE,
                        aiResponseJson = rawJson,
                        linkedTransactionId = duplicate.id,
                        errorMessage = "Merged with existing transaction #${duplicate.id}"
                    )
                )
                return ProcessOutcome.Duplicate(duplicate.id)
            }

            if (accounts.isEmpty()) {
                val msg = "No active accounts available to record transaction."
                repository.updateRawNotification(
                    raw.copy(
                        status = NotificationProcessStatus.FAILED,
                        aiResponseJson = rawJson,
                        errorMessage = msg
                    )
                )
                return ProcessOutcome.PermanentFailure(msg)
            }

            val matchedAccount = parsed.accountId?.let { id -> accounts.firstOrNull { it.id == id } }
            val resolvedAccount = matchedAccount ?: accounts.first()
            var accountUnresolved = matchedAccount == null

            val resolvedToAccount = if (txType == TransactionType.TRANSFER) {
                val target = parsed.toAccountId?.let { id ->
                    accounts.firstOrNull { it.id == id && it.id != resolvedAccount.id }
                }
                if (target == null) accountUnresolved = true
                target
            } else {
                null
            }

            var categoryUnresolved = false
            val resolvedCategory = if (txType == TransactionType.TRANSFER) {
                null
            } else {
                val expectedCatType = if (txType == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
                val matched = parsed.categoryId?.let { id ->
                    categories.firstOrNull { it.id == id && it.type == expectedCatType }
                }
                if (matched != null) {
                    matched
                } else {
                    categoryUnresolved = true
                    categories.firstOrNull {
                        it.type == expectedCatType && it.name.contains("Other", ignoreCase = true)
                    } ?: categories.firstOrNull { it.type == expectedCatType }
                }
            }

            val needsReview = !settings.aiAutoRecordEnabled ||
                parsed.confidence < settings.confidenceThreshold ||
                accountUnresolved ||
                categoryUnresolved

            val rateToBase = repository.getExchangeRateToBase(currency, settings.baseCurrency)
            val baseAmount = LedgerCalculations.computeBaseAmount(parsed.amount, rateToBase)

            val tx = TransactionEntity(
                type = txType,
                amount = parsed.amount,
                currency = currency,
                exchangeRateToBase = rateToBase,
                baseAmount = baseAmount,
                accountId = resolvedAccount.id,
                toAccountId = resolvedToAccount?.id,
                categoryId = resolvedCategory?.id,
                merchant = merchantName,
                note = parsed.note ?: raw.text.take(120),
                timestamp = raw.postedAt,
                source = TransactionSource.AI_NOTIFICATION,
                aiConfidence = parsed.confidence,
                needsReview = needsReview,
                rawNotificationId = raw.id
            )

            val txId = repository.saveTransaction(tx)
            val savedTx = tx.copy(id = txId)

            repository.updateRawNotification(
                raw.copy(
                    status = NotificationProcessStatus.RECORDED,
                    aiResponseJson = rawJson,
                    linkedTransactionId = txId,
                    errorMessage = null
                )
            )

            Triple(savedTx, resolvedAccount.name, resolvedCategory?.name.orEmpty())
        }

        TransactionAlertNotifier.showRecordedTransactionAlert(
            context = context,
            transaction = savedTx,
            accountName = accountName,
            categoryName = categoryName
        )

        val budgetAlerts = repository.evaluateBudgetAlerts(savedTx.timestamp)
        for (alert in budgetAlerts) {
            TransactionAlertNotifier.showBudgetAlert(context, alert, settings.baseCurrency)
        }

        return ProcessOutcome.Recorded(
            transactionId = savedTx.id,
            needsReview = savedTx.needsReview,
            summary = "${savedTx.type.displayName} ${savedTx.amount} ${savedTx.currency}"
        )
    }
}
