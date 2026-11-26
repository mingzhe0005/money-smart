package com.moneysmart.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moneysmart.app.BuildConfig
import com.moneysmart.app.MoneySmartApp
import com.moneysmart.app.data.local.AppSettings
import com.moneysmart.app.data.local.ThemeMode
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.BudgetEntity
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.NotificationProcessStatus
import com.moneysmart.app.data.local.entity.RawNotificationEntity
import com.moneysmart.app.data.local.entity.RecurringTransactionEntity
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.remote.AiParsedNotification
import com.moneysmart.app.data.remote.AppUpdateInfo
import com.moneysmart.app.data.remote.ProbedGeminiModel
import com.moneysmart.app.data.repository.AccountWithBalance
import com.moneysmart.app.data.repository.BudgetProgress
import com.moneysmart.app.data.repository.MoneyManagerImportResult
import com.moneysmart.app.data.repository.MoneySmartBackupSnapshot
import com.moneysmart.app.data.repository.SplitBillWithParticipants
import com.moneysmart.app.notification.ProcessOutcome
import com.moneysmart.app.security.AppLockManager
import com.moneysmart.app.worker.AutoBackupWorker
import com.moneysmart.app.worker.ProcessNotificationWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class MoneySmartViewModel(application: Application) : AndroidViewModel(application) {

    private val container = MoneySmartApp.container(application)
    val repository = container.repository
    val settingsDataStore = container.settingsDataStore
    val backupRepository = container.backupRepository
    val moneyManagerImporter = container.moneyManagerImporter
    val geminiApiClient = container.geminiApiClient
    val notificationProcessor = container.notificationProcessor
    private val appUpdateChecker = container.appUpdateChecker

    val settings: StateFlow<AppSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val accountsWithBalances: StateFlow<List<AccountWithBalance>> = repository.observeAccountsWithBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeAccounts: StateFlow<List<AccountEntity>> = repository.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repository.observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTransactions: StateFlow<List<TransactionEntity>> = repository.observeAllActiveTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val needsReviewCount: StateFlow<Int> = repository.observeNeedsReviewCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val selectedMonth: MutableStateFlow<YearMonth> = MutableStateFlow(YearMonth.now())

    val budgetProgressForSelectedMonth: StateFlow<List<BudgetProgress>> = selectedMonth
        .flatMapLatest { ym -> repository.observeBudgetProgressForMonth(ym) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recurringList: StateFlow<List<RecurringTransactionEntity>> = repository.observeAllRecurring()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rawNotifications: StateFlow<List<RawNotificationEntity>> = repository.observeRecentNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val splitBills: StateFlow<List<SplitBillWithParticipants>> = repository.observeSplitBillsWithParticipants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }

    fun setMonth(ym: YearMonth) {
        selectedMonth.value = ym
    }

    suspend fun getTransaction(id: Long): TransactionEntity? = repository.getTransactionById(id)

    suspend fun getExchangeRateToBase(txCurrency: String, baseCurrency: String): Double =
        repository.getExchangeRateToBase(txCurrency, baseCurrency)

    fun saveTransaction(tx: TransactionEntity, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.saveTransaction(tx)
            repository.evaluateBudgetAlerts(tx.timestamp)
            onSaved(id)
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { repository.softDeleteTransaction(id) }
    }

    fun deleteTransactions(ids: List<Long>) {
        viewModelScope.launch { repository.softDeleteTransactions(ids) }
    }

    fun restoreTransaction(id: Long) {
        viewModelScope.launch { repository.restoreTransaction(id) }
    }

    fun restoreTransactions(ids: List<Long>) {
        viewModelScope.launch { repository.restoreTransactions(ids) }
    }

    fun markReviewed(id: Long) {
        viewModelScope.launch { repository.markTransactionReviewed(id) }
    }

    fun markTransactionsReviewed(ids: List<Long>) {
        viewModelScope.launch { repository.markTransactionsReviewed(ids) }
    }

    fun createSplitBill(
        title: String,
        totalAmount: Double,
        currency: String,
        paidAccountId: Long,
        categoryId: Long?,
        existingExpenseTxId: Long? = null,
        participants: List<Pair<String, Double>>,
        note: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onCreated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.createSplitBill(
                title = title,
                totalAmount = totalAmount,
                currency = currency,
                paidAccountId = paidAccountId,
                categoryId = categoryId,
                existingExpenseTxId = existingExpenseTxId,
                participants = participants,
                note = note,
                timestamp = timestamp
            )
            onCreated()
        }
    }

    fun settleSplitParticipant(participantId: Long, repaidAccountId: Long, paidAmount: Double) {
        viewModelScope.launch { repository.settleParticipant(participantId, repaidAccountId, paidAmount) }
    }

    fun unsettleSplitParticipant(participantId: Long) {
        viewModelScope.launch { repository.unsettleParticipant(participantId) }
    }

    fun deleteSplitBill(billId: Long) {
        viewModelScope.launch { repository.deleteSplitBill(billId) }
    }

    fun saveAccount(account: AccountEntity) {
        viewModelScope.launch { repository.saveAccount(account) }
    }

    fun saveAccountWithTargetBalance(account: AccountEntity, targetCurrentBalance: Double) {
        viewModelScope.launch {
            repository.saveAccountWithTargetBalance(account, targetCurrentBalance)
        }
    }

    suspend fun hasAnyAccounts(): Boolean = repository.hasAnyAccounts()

    fun completeOnboarding(
        baseCurrency: String,
        starterAccounts: List<AccountEntity>,
        aiCaptureEnabled: Boolean,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.completeOnboarding(baseCurrency, starterAccounts, aiCaptureEnabled)
            onDone()
        }
    }

    fun setAiCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAiCaptureEnabled(enabled) }
    }

    suspend fun syncLiveInvestmentsNow(): Result<Int> = repository.syncLiveInvestmentValues()

    fun archiveAccount(account: AccountEntity) {
        viewModelScope.launch { repository.archiveAccount(account) }
    }

    fun deleteAccountPermanently(accountId: Long) {
        viewModelScope.launch { repository.deleteAccountPermanently(accountId) }
    }

    fun rechargeAccountNow(accountId: Long, onRecharged: (Double) -> Unit = {}) {
        viewModelScope.launch {
            val recharged = repository.rechargeAccountNowIfNegative(accountId)
            onRecharged(recharged)
        }
    }

    fun payCreditCardBill(
        cardAccountId: Long,
        fromAccountId: Long,
        amount: Double,
        note: String = "",
        onResult: (Result<Long>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.payCreditCardBill(
                cardAccountId = cardAccountId,
                fromAccountId = fromAccountId,
                amount = amount,
                note = note
            )
            onResult(result)
        }
    }

    fun linkFriendRepaymentsToExpense(
        expenseTxId: Long,
        incomeTxIds: List<Long>,
        onDone: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            val count = repository.linkExistingIncomeAsReimbursements(expenseTxId, incomeTxIds)
            onDone(count)
        }
    }

    fun saveCategory(category: CategoryEntity) {
        viewModelScope.launch { repository.saveCategory(category) }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch { repository.deleteCategory(categoryId) }
    }

    fun saveBudget(budget: BudgetEntity) {
        viewModelScope.launch { repository.saveBudget(budget) }
    }

    fun deleteBudget(id: Long) {
        viewModelScope.launch { repository.deleteBudget(id) }
    }

    fun saveRecurring(recurring: RecurringTransactionEntity) {
        viewModelScope.launch { repository.saveRecurring(recurring) }
    }

    fun deleteRecurring(id: Long) {
        viewModelScope.launch { repository.deleteRecurring(id) }
    }

    fun executeRecurringNow(id: Long) {
        viewModelScope.launch { repository.executeRecurringById(id) }
    }

    fun retryRawNotification(rawId: Long, onResult: (ProcessOutcome) -> Unit = {}) {
        viewModelScope.launch {
            val raw = repository.getRawNotificationById(rawId) ?: return@launch
            repository.updateRawNotification(raw.copy(status = NotificationProcessStatus.PENDING, errorMessage = null))
            val outcome = notificationProcessor.processRawNotification(rawId)
            onResult(outcome)
        }
    }

    fun enqueueRawNotificationWorker(rawId: Long) {
        ProcessNotificationWorker.enqueue(getApplication(), rawId)
    }

    fun retryAllPendingNotifications(includeFailed: Boolean = true, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val count = notificationProcessor.processAllPendingNotifications(includeFailed = includeFailed)
            onDone(count)
        }
    }

    fun clearNotificationLog() {
        viewModelScope.launch { repository.clearRawNotificationLog() }
    }

    suspend fun simulateAiNotification(
        appName: String,
        title: String,
        text: String
    ): Result<Pair<AiParsedNotification, String>> {
        return notificationProcessor.simulateParse(appName, title, text)
    }

    suspend fun recordSimulatedNotification(
        appName: String,
        title: String,
        text: String
    ): ProcessOutcome {
        val rawId = repository.insertRawNotification(
            RawNotificationEntity(
                packageName = "com.moneysmart.simulator",
                appName = appName.ifBlank { "Simulator" },
                title = title,
                text = text,
                postedAt = System.currentTimeMillis(),
                status = NotificationProcessStatus.PENDING
            )
        )
        return notificationProcessor.processRawNotification(rawId)
    }

    fun saveGeminiConfig(apiKey: String, model: String) {
        viewModelScope.launch { settingsDataStore.setGeminiConfig(apiKey, model) }
    }

    suspend fun testGeminiConnection(apiKey: String, model: String): Result<String> =
        geminiApiClient.testConnection(apiKey, model)

    suspend fun fetchAvailableGeminiModels(apiKey: String): Result<List<ProbedGeminiModel>> =
        geminiApiClient.listUsableFreeModels(apiKey)

    fun setBaseCurrency(currency: String) {
        viewModelScope.launch {
            settingsDataStore.setBaseCurrency(currency)
            repository.syncExchangeRates(force = true)
        }
    }

    suspend fun syncExchangeRatesNow(): Result<Int> = repository.syncExchangeRates(force = true)

    fun toggleAllowedPackage(packageName: String, enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.toggleAllowedPackage(packageName, enabled) }
    }

    fun setAiPreferences(autoRecord: Boolean, threshold: Float) {
        viewModelScope.launch { settingsDataStore.setAiPreferences(autoRecord, threshold) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun enablePinLock(pin: String, biometricEnabled: Boolean) {
        viewModelScope.launch {
            val salt = AppLockManager.generateSalt()
            val hash = AppLockManager.hashPin(pin, salt)
            settingsDataStore.setAppLock(
                enabled = true,
                pinHash = hash,
                pinSalt = salt,
                biometricEnabled = biometricEnabled
            )
        }
    }

    fun disableAppLock() {
        viewModelScope.launch {
            settingsDataStore.setAppLock(enabled = false, pinHash = "", pinSalt = "", biometricEnabled = false)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setBiometricEnabled(enabled) }
    }

    fun setBackupFolderUri(uri: String) {
        viewModelScope.launch { settingsDataStore.setBackupFolderUri(uri) }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAutoBackupEnabled(enabled)
            if (enabled) {
                AutoBackupWorker.schedulePeriodic(getApplication())
            } else {
                AutoBackupWorker.cancel(getApplication())
            }
        }
    }

    suspend fun backupJsonNow(treeUri: Uri): Result<String> = backupRepository.exportJsonToTree(treeUri)
    suspend fun exportCsvNow(treeUri: Uri): Result<String> = backupRepository.exportCsvToTree(treeUri)
    suspend fun restoreJson(fileUri: Uri): Result<MoneySmartBackupSnapshot> =
        backupRepository.restoreFromJsonUri(fileUri)

    suspend fun importMoneyManagerFile(fileUri: Uri): Result<MoneyManagerImportResult> =
        moneyManagerImporter.importFromUri(fileUri)

    suspend fun checkForAppUpdate(): Result<AppUpdateInfo> =
        appUpdateChecker.checkLatestRelease(BuildConfig.VERSION_NAME)
}
