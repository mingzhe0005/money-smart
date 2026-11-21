package com.moneysmart.app.data.repository

import androidx.room.withTransaction
import com.moneysmart.app.data.local.DefaultSeedData
import com.moneysmart.app.data.local.MoneySmartDatabase
import com.moneysmart.app.data.local.SettingsDataStore
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.data.local.entity.BudgetEntity
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.ExchangeRateEntity
import com.moneysmart.app.data.local.entity.RawNotificationEntity
import com.moneysmart.app.data.local.entity.RecurringTransactionEntity
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionSource
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.data.remote.ExchangeRateApiClient
import com.moneysmart.app.data.remote.MarketPriceApiClient
import com.moneysmart.app.util.FormatUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import kotlin.math.roundToLong

data class AccountWithBalance(
    val account: AccountEntity,
    val currentBalance: Double,
    val balanceInBaseCurrency: Double = currentBalance,
    val isLiveMarketValue: Boolean = false
)

data class BudgetProgress(
    val budget: BudgetEntity,
    val categoryName: String,
    val spentAmount: Double
) {
    val progressRatio: Float
        get() = if (budget.limitAmount > 0.0) (spentAmount / budget.limitAmount).toFloat() else 0f
}

data class BudgetAlertEvent(
    val budgetId: Long,
    val categoryName: String,
    val spentAmount: Double,
    val limitAmount: Double,
    val percentUsed: Int,
    val isExceeded: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class MoneyRepository(
    private val db: MoneySmartDatabase,
    private val settingsDataStore: SettingsDataStore,
    private val exchangeRateApiClient: ExchangeRateApiClient,
    private val marketPriceApiClient: MarketPriceApiClient
) {
    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val transactionDao = db.transactionDao()
    private val budgetDao = db.budgetDao()
    private val recurringDao = db.recurringDao()
    private val rawNotificationDao = db.rawNotificationDao()
    private val exchangeRateDao = db.exchangeRateDao()

    fun observeActiveAccounts(): Flow<List<AccountEntity>> = accountDao.observeActiveAccounts()
    fun observeAllAccounts(): Flow<List<AccountEntity>> = accountDao.observeAllAccounts()
    fun observeAllCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAllCategories()
    fun observeAllActiveTransactions(): Flow<List<TransactionEntity>> = transactionDao.observeAllActiveTransactions()
    fun observeNeedsReviewCount(): Flow<Int> = transactionDao.observeNeedsReviewCount()
    fun observeAllRecurring(): Flow<List<RecurringTransactionEntity>> = recurringDao.observeAllRecurring()
    fun observeRecentNotifications(): Flow<List<RawNotificationEntity>> = rawNotificationDao.observeRecentNotifications()
    fun observeBudgetsForMonth(yearMonth: String): Flow<List<BudgetEntity>> = budgetDao.observeBudgetsForMonth(yearMonth)

    fun observeAccountsWithBalances(): Flow<List<AccountWithBalance>> {
        val settingsAndRates = settingsDataStore.settingsFlow.flatMapLatest { settings ->
            exchangeRateDao.observeRatesForBase(settings.baseCurrency.uppercase()).map { rates ->
                settings to rates.associateBy { it.targetCurrency.uppercase() }
            }
        }
        return combine(
            accountDao.observeActiveAccounts(),
            transactionDao.observeAllActiveTransactions(),
            settingsAndRates
        ) { accounts, transactions, (settings, ratesMap) ->
            accounts.map { acc ->
                val ledgerBalance = LedgerCalculations.computeAccountBalance(
                    account = acc,
                    transactions = transactions,
                    baseCurrency = settings.baseCurrency
                )
                val hasLiveValue = acc.type == AccountType.INVESTMENT &&
                    !acc.marketSymbol.isNullOrBlank() &&
                    (acc.holdingUnits ?: 0.0) > 0.0 &&
                    acc.lastSyncedMarketValue != null
                val displayBalance = if (hasLiveValue) acc.lastSyncedMarketValue!! else ledgerBalance
                val rateToBase = if (acc.currency.equals(settings.baseCurrency, ignoreCase = true)) {
                    1.0
                } else {
                    val apiRate = ratesMap[acc.currency.uppercase()]?.rate
                    LedgerCalculations.exchangeRateToBaseFromApiRate(
                        txCurrency = acc.currency,
                        baseCurrency = settings.baseCurrency,
                        apiRateBaseToTx = apiRate
                    )
                }
                val baseBalance = LedgerCalculations.computeBaseAmount(displayBalance, rateToBase)
                AccountWithBalance(
                    account = acc,
                    currentBalance = displayBalance,
                    balanceInBaseCurrency = baseBalance,
                    isLiveMarketValue = hasLiveValue
                )
            }
        }
    }

    fun observeBudgetProgressForMonth(yearMonth: YearMonth): Flow<List<BudgetProgress>> {
        val key = FormatUtils.yearMonthKey(yearMonth)
        val (startMillis, endMillis) = FormatUtils.monthRangeMillis(yearMonth)
        return combine(
            budgetDao.observeBudgetsForMonth(key),
            categoryDao.observeAllCategories(),
            transactionDao.observeTransactionsInRange(startMillis, endMillis)
        ) { budgets, categories, monthTransactions ->
            val categoryMap = categories.associateBy { it.id }
            val expenses = monthTransactions.filter { !it.isDeleted && it.type == TransactionType.EXPENSE }
            budgets.map { budget ->
                val spent = if (budget.categoryId == null) {
                    expenses.sumOf { it.baseAmount }
                } else {
                    val childIds = categories.filter { it.parentId == budget.categoryId }.map { it.id }.toSet()
                    val relevantIds = childIds + budget.categoryId
                    expenses.filter { it.categoryId != null && it.categoryId in relevantIds }
                        .sumOf { it.baseAmount }
                }
                val label = budget.categoryId?.let { categoryMap[it]?.name } ?: "Overall Monthly Budget"
                BudgetProgress(
                    budget = budget,
                    categoryName = label,
                    spentAmount = spent
                )
            }
        }
    }

    suspend fun getActiveAccounts(): List<AccountEntity> = accountDao.getActiveAccounts()
    suspend fun getAllCategories(): List<CategoryEntity> = categoryDao.getAllCategories()
    suspend fun getAllActiveTransactions(): List<TransactionEntity> = transactionDao.getAllActiveTransactions()
    suspend fun getTransactionById(id: Long): TransactionEntity? = transactionDao.getById(id)
    suspend fun getAccountById(id: Long): AccountEntity? = accountDao.getById(id)
    suspend fun getCategoryById(id: Long): CategoryEntity? = categoryDao.getById(id)

    suspend fun saveAccount(account: AccountEntity): Long {
        return if (account.id == 0L) accountDao.insert(account) else {
            accountDao.update(account)
            account.id
        }
    }

    suspend fun saveAccountWithTargetBalance(account: AccountEntity, targetCurrentBalance: Double): Long {
        if (account.id == 0L) {
            return accountDao.insert(account.copy(initialBalance = targetCurrentBalance))
        }
        val settings = settingsDataStore.getCurrentSettings()
        val activeTxs = transactionDao.getAllActiveTransactions()
        val calibratedInitial = LedgerCalculations.computeCalibratedInitialBalance(
            account = account,
            transactions = activeTxs,
            targetCurrentBalance = targetCurrentBalance,
            baseCurrency = settings.baseCurrency
        )
        accountDao.update(account.copy(initialBalance = calibratedInitial))
        return account.id
    }

    suspend fun applyScreenshotSnapshotBalances(): Int {
        val settings = settingsDataStore.getCurrentSettings()
        return DefaultSeedData.applySnapshotBalances(db, settings.baseCurrency)
    }

    suspend fun syncLiveInvestmentValues(): Result<Int> {
        val activeAccounts = accountDao.getActiveAccounts()
        val liveTargets = activeAccounts.filter {
            it.type == AccountType.INVESTMENT &&
                !it.marketSymbol.isNullOrBlank() &&
                (it.holdingUnits ?: 0.0) > 0.0
        }
        if (liveTargets.isEmpty()) return Result.success(0)

        syncExchangeRates(force = false)
        val settings = settingsDataStore.getCurrentSettings()
        var syncedCount = 0
        var lastError: Throwable? = null
        val now = System.currentTimeMillis()

        for (acc in liveTargets) {
            val units = acc.holdingUnits ?: continue
            val symbol = acc.marketSymbol ?: continue
            val quoteRes = marketPriceApiClient.fetchQuote(symbol)
            if (quoteRes.isFailure) {
                lastError = quoteRes.exceptionOrNull()
                continue
            }
            val quote = quoteRes.getOrThrow()
            val priceInAccountCurrency = if (quote.currency.equals(acc.currency, ignoreCase = true)) {
                quote.price
            } else {
                val quoteToBase = getExchangeRateToBase(quote.currency, settings.baseCurrency)
                val accToBase = getExchangeRateToBase(acc.currency, settings.baseCurrency)
                if (accToBase > 0.0) quote.price * (quoteToBase / accToBase) else quote.price
            }
            val liveValue = ((units * priceInAccountCurrency) * 100.0).roundToLong() / 100.0
            accountDao.update(
                acc.copy(
                    lastSyncedMarketValue = liveValue,
                    lastMarketSyncAt = now
                )
            )
            syncedCount++
        }

        return if (syncedCount > 0 || lastError == null) {
            Result.success(syncedCount)
        } else {
            Result.failure(lastError!!)
        }
    }

    suspend fun archiveAccount(account: AccountEntity) {
        accountDao.update(account.copy(isArchived = true))
    }

    suspend fun saveCategory(category: CategoryEntity): Long {
        return if (category.id == 0L) categoryDao.insert(category) else {
            categoryDao.update(category)
            category.id
        }
    }

    suspend fun deleteCategory(categoryId: Long) {
        categoryDao.deleteWithChildren(categoryId)
    }

    suspend fun saveTransaction(tx: TransactionEntity): Long {
        val now = System.currentTimeMillis()
        val computedBase = LedgerCalculations.computeBaseAmount(tx.amount, tx.exchangeRateToBase)
        val clean = tx.copy(baseAmount = computedBase, updatedAt = now)
        if (clean.id != 0L) {
            transactionDao.update(clean)
            return clean.id
        }

        val insertedId = transactionDao.insert(clean.copy(createdAt = now))
        if (clean.type == TransactionType.EXPENSE) {
            triggerAutoRechargeIfNeeded(clean, now)
        }
        return insertedId
    }

    private suspend fun triggerAutoRechargeIfNeeded(expenseTx: TransactionEntity, now: Long) {
        val account = accountDao.getById(expenseTx.accountId) ?: return
        val sourceId = account.autoRechargeSourceAccountId ?: return
        if (sourceId == account.id) return
        val sourceAccount = accountDao.getById(sourceId) ?: return

        val settings = settingsDataStore.getCurrentSettings()
        val activeTxs = transactionDao.getAllActiveTransactions()
        val currentBalance = LedgerCalculations.computeAccountBalance(
            account = account,
            transactions = activeTxs,
            baseCurrency = settings.baseCurrency
        )
        val shortfall = LedgerCalculations.computeAutoRechargeShortfall(
            currentBalance = currentBalance,
            minBalance = account.autoRechargeMinBalance
        )
        if (shortfall <= 0.0) return

        val rateToBase = getExchangeRateToBase(account.currency, settings.baseCurrency)
        val transfer = TransactionEntity(
            type = TransactionType.TRANSFER,
            amount = shortfall,
            currency = account.currency,
            exchangeRateToBase = rateToBase,
            baseAmount = LedgerCalculations.computeBaseAmount(shortfall, rateToBase),
            accountId = sourceAccount.id,
            toAccountId = account.id,
            categoryId = null,
            merchant = "Auto-Recharge (${sourceAccount.name} → ${account.name})",
            note = "Automatic top-up when ${account.name} went below ${account.autoRechargeMinBalance}",
            timestamp = expenseTx.timestamp + 1_000L,
            source = TransactionSource.RECURRING,
            needsReview = false,
            createdAt = now,
            updatedAt = now
        )
        transactionDao.insert(transfer)
    }

    suspend fun softDeleteTransaction(id: Long) {
        transactionDao.softDelete(id)
    }

    suspend fun softDeleteTransactions(ids: List<Long>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        db.withTransaction {
            ids.chunked(500).forEach { chunk ->
                transactionDao.softDeleteBatch(chunk, now)
            }
        }
    }

    suspend fun restoreTransaction(id: Long) {
        transactionDao.restore(id)
    }

    suspend fun restoreTransactions(ids: List<Long>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        db.withTransaction {
            ids.chunked(500).forEach { chunk ->
                transactionDao.restoreBatch(chunk, now)
            }
        }
    }

    suspend fun markTransactionReviewed(id: Long) {
        transactionDao.markReviewed(id)
    }

    suspend fun markTransactionsReviewed(ids: List<Long>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        db.withTransaction {
            ids.chunked(500).forEach { chunk ->
                transactionDao.markReviewedBatch(chunk, now)
            }
        }
    }

    suspend fun findPotentialDuplicate(
        amount: Double,
        currency: String,
        timestamp: Long,
        windowMillis: Long = LedgerCalculations.DEFAULT_DEDUP_WINDOW_MILLIS
    ): TransactionEntity? {
        val candidates = transactionDao.findPotentialDuplicates(
            amount = amount,
            currency = currency,
            startMillis = timestamp - windowMillis,
            endMillis = timestamp + windowMillis
        )
        return LedgerCalculations.findDuplicateTransaction(
            existing = candidates,
            amount = amount,
            currency = currency,
            timestamp = timestamp,
            windowMillis = windowMillis
        )
    }

    suspend fun getExchangeRateToBase(txCurrency: String, baseCurrency: String): Double {
        if (txCurrency.equals(baseCurrency, ignoreCase = true)) return 1.0
        val cached = exchangeRateDao.getRate(baseCurrency.uppercase(), txCurrency.uppercase())
        if (cached != null && cached.rate > 0.0) {
            return LedgerCalculations.exchangeRateToBaseFromApiRate(txCurrency, baseCurrency, cached.rate)
        }
        // Try reverse if cached under txCurrency -> baseCurrency
        val reverse = exchangeRateDao.getRate(txCurrency.uppercase(), baseCurrency.uppercase())
        if (reverse != null && reverse.rate > 0.0) {
            return reverse.rate
        }
        return 1.0
    }

    suspend fun syncExchangeRates(force: Boolean = false): Result<Int> {
        val settings = settingsDataStore.getCurrentSettings()
        val now = System.currentTimeMillis()
        val twelveHours = 12 * 60 * 60 * 1000L
        if (!force && (now - settings.lastRateSyncTimestamp) < twelveHours) {
            return Result.success(0)
        }
        val base = settings.baseCurrency.uppercase()
        val fetched = exchangeRateApiClient.fetchRates(base)
        return fetched.map { rateMap ->
            val entities = rateMap.map { (target, rate) ->
                ExchangeRateEntity(
                    baseCurrency = base,
                    targetCurrency = target,
                    rate = rate,
                    updatedAt = now
                )
            }
            exchangeRateDao.upsertAll(entities)
            settingsDataStore.setLastRateSyncTimestamp(now)
            entities.size
        }
    }

    suspend fun saveBudget(budget: BudgetEntity): Long = budgetDao.upsert(budget)

    suspend fun deleteBudget(id: Long) = budgetDao.deleteById(id)

    suspend fun evaluateBudgetAlerts(transactionTimestamp: Long): List<BudgetAlertEvent> {
        val yearMonthKey = FormatUtils.yearMonthKeyFromMillis(transactionTimestamp)
        val ym = YearMonth.parse(yearMonthKey)
        val (startMillis, endMillis) = FormatUtils.monthRangeMillis(ym)
        val budgets = budgetDao.getBudgetsForMonth(yearMonthKey)
        if (budgets.isEmpty()) return emptyList()

        val categories = categoryDao.getAllCategories()
        val categoryMap = categories.associateBy { it.id }
        val alerts = mutableListOf<BudgetAlertEvent>()

        for (budget in budgets) {
            if (budget.limitAmount <= 0.0) continue
            val spent = if (budget.categoryId == null) {
                transactionDao.getExpensesSumInRange(startMillis, endMillis)
            } else {
                val childIds = categories.filter { it.parentId == budget.categoryId }.map { it.id }
                transactionDao.getExpensesSumForCategoriesInRange(
                    categoryIds = listOf(budget.categoryId) + childIds,
                    startMillis = startMillis,
                    endMillis = endMillis
                )
            }
            val pct = ((spent / budget.limitAmount) * 100.0).toInt()
            val label = budget.categoryId?.let { categoryMap[it]?.name } ?: "Overall Monthly Budget"

            if (pct >= 100 && !budget.alertSent100) {
                budgetDao.update(budget.copy(alertSent80 = true, alertSent100 = true))
                alerts.add(BudgetAlertEvent(budget.id, label, spent, budget.limitAmount, pct, isExceeded = true))
            } else if (pct >= budget.alertThresholdPercent && !budget.alertSent80) {
                budgetDao.update(budget.copy(alertSent80 = true))
                alerts.add(BudgetAlertEvent(budget.id, label, spent, budget.limitAmount, pct, isExceeded = false))
            }
        }
        return alerts
    }

    suspend fun saveRecurring(recurring: RecurringTransactionEntity): Long {
        return if (recurring.id == 0L) recurringDao.insert(recurring) else {
            recurringDao.update(recurring)
            recurring.id
        }
    }

    suspend fun deleteRecurring(id: Long) = recurringDao.deleteById(id)

    suspend fun executeRecurringById(id: Long): Long? {
        val rec = recurringDao.getById(id) ?: return null
        val settings = settingsDataStore.getCurrentSettings()
        val rate = getExchangeRateToBase(rec.currency, settings.baseCurrency)
        val now = System.currentTimeMillis()
        val txId = saveTransaction(
            TransactionEntity(
                type = rec.type,
                amount = rec.amount,
                currency = rec.currency,
                exchangeRateToBase = rate,
                baseAmount = LedgerCalculations.computeBaseAmount(rec.amount, rate),
                accountId = rec.accountId,
                toAccountId = rec.toAccountId,
                categoryId = rec.categoryId,
                merchant = rec.merchant.ifBlank { rec.title },
                note = rec.note.ifBlank { "Recurring: ${rec.title}" },
                timestamp = now,
                source = TransactionSource.RECURRING,
                needsReview = false
            )
        )
        val nextDue = LedgerCalculations.computeNextDueMillis(
            currentDueMillis = maxOf(rec.nextDueMillis, now),
            frequency = rec.frequency,
            intervalCount = rec.intervalCount
        )
        recurringDao.update(rec.copy(lastExecutedMillis = now, nextDueMillis = nextDue))
        return txId
    }

    suspend fun processDueRecurringTransactions(): Int {
        val now = System.currentTimeMillis()
        val dueList = recurringDao.getDueRecurring(now)
        if (dueList.isEmpty()) return 0
        val settings = settingsDataStore.getCurrentSettings()
        var createdCount = 0

        for (rec in dueList) {
            var nextDue = rec.nextDueMillis
            var guard = 0
            while (nextDue <= now && guard < 12) {
                val rate = getExchangeRateToBase(rec.currency, settings.baseCurrency)
                saveTransaction(
                    TransactionEntity(
                        type = rec.type,
                        amount = rec.amount,
                        currency = rec.currency,
                        exchangeRateToBase = rate,
                        baseAmount = LedgerCalculations.computeBaseAmount(rec.amount, rate),
                        accountId = rec.accountId,
                        toAccountId = rec.toAccountId,
                        categoryId = rec.categoryId,
                        merchant = rec.merchant.ifBlank { rec.title },
                        note = rec.note.ifBlank { "Recurring: ${rec.title}" },
                        timestamp = nextDue,
                        source = TransactionSource.RECURRING,
                        needsReview = false
                    )
                )
                createdCount++
                nextDue = LedgerCalculations.computeNextDueMillis(
                    currentDueMillis = nextDue,
                    frequency = rec.frequency,
                    intervalCount = rec.intervalCount
                )
                guard++
            }
            recurringDao.update(rec.copy(lastExecutedMillis = now, nextDueMillis = nextDue))
        }
        return createdCount
    }

    // Raw Notifications
    suspend fun insertRawNotification(notification: RawNotificationEntity): Long =
        rawNotificationDao.insert(notification)

    suspend fun updateRawNotification(notification: RawNotificationEntity) =
        rawNotificationDao.update(notification)

    suspend fun getRawNotificationById(id: Long): RawNotificationEntity? =
        rawNotificationDao.getById(id)

    suspend fun findRecentIdenticalRawNotification(
        packageName: String,
        title: String,
        text: String,
        sinceMillis: Long
    ): RawNotificationEntity? =
        rawNotificationDao.findRecentIdentical(packageName, title, text, sinceMillis)

    suspend fun clearRawNotificationLog() = rawNotificationDao.deleteAll()
}
