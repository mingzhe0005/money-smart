package com.moneysmart.app.notification

import android.content.Context
import com.moneysmart.app.data.local.CategoryOrganizer
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
    private val drainMutex = Mutex()

    suspend fun processAllPendingNotifications(includeFailed: Boolean = false): Int = drainMutex.withLock {
        val settings = settingsDataStore.getCurrentSettings()
        if (!settings.aiCaptureEnabled || settings.geminiApiKey.isBlank()) return@withLock 0

        repository.resetStaleProcessingNotifications(System.currentTimeMillis() - 120_000L)
        val queue = if (includeFailed) {
            repository.getRetryableRawNotifications()
        } else {
            repository.getPendingRawNotifications()
        }
        var processedCount = 0
        for (item in queue) {
            if (includeFailed && item.status == NotificationProcessStatus.FAILED) {
                repository.updateRawNotification(
                    item.copy(status = NotificationProcessStatus.PENDING, errorMessage = null)
                )
            }
            when (val outcome = processRawNotification(item.id)) {
                is ProcessOutcome.Recorded,
                is ProcessOutcome.Duplicate,
                is ProcessOutcome.Ignored -> processedCount++
                is ProcessOutcome.RetryableFailure -> {
                    if (isLikelyOfflineError(outcome.message)) break
                }
                is ProcessOutcome.PermanentFailure -> Unit
            }
        }
        processedCount
    }

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

            val accountsById = accounts.associateBy { it.id }
            val matchedAiAccount = parsed.accountId?.let { accountsById[it] }
            val lexSource = LedgerCalculations.findMentionedAccount(
                accounts = accounts,
                appName = raw.appName,
                packageName = raw.packageName,
                title = raw.title,
                text = raw.text
            )
            var matchedAccount = matchedAiAccount ?: lexSource
            var sourceVerified = matchedAccount != null && LedgerCalculations.isAccountMentionedInNotification(
                account = matchedAccount,
                appName = raw.appName,
                packageName = raw.packageName,
                title = raw.title,
                text = raw.text,
                allAccounts = accounts
            )

            var rawToAccount = if (txType == TransactionType.TRANSFER) {
                val aiDest = parsed.toAccountId?.let { id ->
                    accounts.firstOrNull { it.id == id && it.id != matchedAccount?.id }
                }
                val aiDestVerified = aiDest != null && LedgerCalculations.isAccountMentionedInNotification(
                    account = aiDest,
                    appName = raw.appName,
                    packageName = raw.packageName,
                    title = raw.title,
                    text = raw.text,
                    allAccounts = accounts,
                    excludeAccount = matchedAccount
                )
                if (aiDestVerified) {
                    aiDest
                } else {
                    LedgerCalculations.findMentionedAccount(
                        accounts = accounts,
                        appName = raw.appName,
                        packageName = raw.packageName,
                        title = raw.title,
                        text = raw.text,
                        excludeAccount = matchedAccount
                    )
                }
            } else {
                null
            }

            // If AI mislabeled an incoming-only bank alert as TRANSFER (dest known or incoming wording, source unverified),
            // treat it as an incoming event on the receiving bank so it won't debit an unrelated account and can pair cleanly.
            var effectiveTxType = txType
            if (txType == TransactionType.TRANSFER) {
                if (!sourceVerified && rawToAccount != null) {
                    val otherSource = LedgerCalculations.findMentionedAccount(
                        accounts = accounts,
                        appName = raw.appName,
                        packageName = raw.packageName,
                        title = raw.title,
                        text = raw.text,
                        excludeAccount = rawToAccount
                    )
                    if (otherSource != null) {
                        matchedAccount = otherSource
                        sourceVerified = true
                    } else {
                        matchedAccount = rawToAccount
                        rawToAccount = null
                        effectiveTxType = TransactionType.INCOME
                    }
                } else if (rawToAccount == null && matchedAccount != null && isLikelyIncomingOnlyAlert(raw.title, raw.text)) {
                    effectiveTxType = TransactionType.INCOME
                }
            }

            val incomingText = "${raw.appName} ${raw.title} ${raw.text}".trim()
            val pairingCandidates = repository.findRecentSameAmountCandidatesWithText(
                amount = parsed.amount,
                currency = currency,
                timestamp = raw.postedAt
            )
            val pairMatch = LedgerCalculations.findTransferCounterpart(
                existingCandidates = pairingCandidates,
                incomingType = effectiveTxType,
                incomingAccountId = matchedAccount?.id,
                incomingToAccountId = rawToAccount?.id,
                incomingAmount = parsed.amount,
                incomingCurrency = currency,
                incomingTimestamp = raw.postedAt,
                incomingText = incomingText,
                incomingMerchant = merchantName,
                incomingNote = parsed.note.orEmpty(),
                accountsById = accountsById
            )

            if (pairMatch != null) {
                val srcAcc = accountsById[pairMatch.sourceAccountId]
                val dstAcc = accountsById[pairMatch.destAccountId]
                val pairLabel = if (srcAcc != null && dstAcc != null) {
                    "${srcAcc.name} → ${dstAcc.name}"
                } else {
                    pairMatch.existing.merchant
                }
                if (pairMatch.shouldUpdateExisting) {
                    val updatedExisting = pairMatch.existing.copy(
                        type = TransactionType.TRANSFER,
                        accountId = pairMatch.sourceAccountId,
                        toAccountId = pairMatch.destAccountId,
                        categoryId = null,
                        isReimbursement = false,
                        merchant = pairLabel,
                        needsReview = !settings.aiAutoRecordEnabled
                    )
                    repository.saveTransaction(updatedExisting)
                    TransactionAlertNotifier.showRecordedTransactionAlert(
                        context = context,
                        transaction = updatedExisting,
                        accountName = pairLabel,
                        categoryName = ""
                    )
                }
                repository.updateRawNotification(
                    raw.copy(
                        status = NotificationProcessStatus.DUPLICATE,
                        aiResponseJson = rawJson,
                        linkedTransactionId = pairMatch.existing.id,
                        errorMessage = "Paired with transfer #${pairMatch.existing.id} ($pairLabel)"
                    )
                )
                return ProcessOutcome.Duplicate(pairMatch.existing.id)
            }

            val duplicate = repository.findPotentialDuplicate(
                amount = parsed.amount,
                currency = currency,
                timestamp = raw.postedAt,
                type = effectiveTxType,
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

            val resolvedAccount = matchedAccount ?: accounts.first()
            var accountUnresolved = matchedAccount == null || !sourceVerified

            val resolvedToAccount = if (effectiveTxType == TransactionType.TRANSFER) {
                if (rawToAccount == null) accountUnresolved = true
                rawToAccount
            } else {
                null
            }

            var categoryUnresolved = false
            val resolvedCategory = if (effectiveTxType == TransactionType.TRANSFER) {
                null
            } else {
                val expectedCatType = if (effectiveTxType == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
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

            val isReimb = effectiveTxType == TransactionType.INCOME &&
                CategoryOrganizer.isReimbursementCategoryName(resolvedCategory?.name)

            val finalMerchant = if (effectiveTxType == TransactionType.TRANSFER && resolvedToAccount != null) {
                "${resolvedAccount.name} → ${resolvedToAccount.name}"
            } else {
                merchantName
            }

            val tx = TransactionEntity(
                type = effectiveTxType,
                amount = parsed.amount,
                currency = currency,
                exchangeRateToBase = rateToBase,
                baseAmount = baseAmount,
                accountId = resolvedAccount.id,
                toAccountId = resolvedToAccount?.id,
                categoryId = resolvedCategory?.id,
                merchant = finalMerchant,
                note = parsed.note ?: raw.text.take(120),
                timestamp = raw.postedAt,
                source = TransactionSource.AI_NOTIFICATION,
                aiConfidence = parsed.confidence,
                needsReview = needsReview,
                rawNotificationId = raw.id,
                isReimbursement = isReimb
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

            val displayAccountName = if (effectiveTxType == TransactionType.TRANSFER && resolvedToAccount != null) {
                "${resolvedAccount.name} → ${resolvedToAccount.name}"
            } else {
                resolvedAccount.name
            }
            Triple(savedTx, displayAccountName, resolvedCategory?.name.orEmpty())
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

    companion object {
        private val INCOMING_ONLY_CUES = listOf("received", "credited", "deposited", "sent you", "paid you")
        private val OUTGOING_CUES = listOf("transferred", "debited", "deducted", "charged", "paid to", "sent to")

        fun isLikelyIncomingOnlyAlert(title: String, text: String): Boolean {
            val lower = "$title $text".lowercase()
            return INCOMING_ONLY_CUES.any { lower.contains(it) } &&
                OUTGOING_CUES.none { lower.contains(it) }
        }

        fun isLikelyOfflineError(message: String?): Boolean {
            if (message.isNullOrBlank()) return false
            val lower = message.lowercase()
            return lower.contains("unable to resolve host") || lower.contains("network error")
        }
    }
}
