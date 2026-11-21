package com.moneysmart.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.repository.LedgerCalculations
import com.moneysmart.app.notification.PaymentNotificationListenerService
import com.moneysmart.app.ui.components.EmptyStateCard
import com.moneysmart.app.ui.components.LabeledActionButton
import com.moneysmart.app.ui.components.MonthSelectorBar
import com.moneysmart.app.ui.components.ResponsiveContentBox
import com.moneysmart.app.ui.components.TopBarAddButton
import com.moneysmart.app.ui.components.TransactionRow
import com.moneysmart.app.ui.components.rememberIsWideScreen
import com.moneysmart.app.ui.theme.ExpenseRed
import com.moneysmart.app.ui.theme.ExpenseRedDark
import com.moneysmart.app.ui.theme.IncomeGreen
import com.moneysmart.app.ui.theme.IncomeGreenDark
import com.moneysmart.app.ui.theme.WarningAmber
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import com.moneysmart.app.util.FormatUtils
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private enum class LedgerViewMode(val label: String) {
    DAILY("Daily"),
    MONTHLY("Monthly"),
    CALENDAR("Calendar")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: MoneySmartViewModel,
    onAddTransaction: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    onBulkDeleted: (List<Long>) -> Unit,
    onOpenAiActivity: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val accounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val needsReviewCount by viewModel.needsReviewCount.collectAsStateWithLifecycle()

    var isListenerEnabled by remember {
        mutableStateOf(PaymentNotificationListenerService.isNotificationListenerEnabled(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isListenerEnabled = PaymentNotificationListenerService.isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isWide = rememberIsWideScreen()
    var bannerDismissed by rememberSaveable { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(LedgerViewMode.DAILY) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var onlyNeedsReview by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var selectedCalendarDate by remember(selectedMonth) {
        mutableStateOf(
            if (selectedMonth == YearMonth.now()) LocalDate.now() else selectedMonth.atDay(1)
        )
    }

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    fun enterSelectionWith(txId: Long) {
        selectionMode = true
        selectedIds = selectedIds + txId
    }

    fun toggleSelection(txId: Long) {
        selectedIds = if (txId in selectedIds) selectedIds - txId else selectedIds + txId
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedIds = emptySet()
    }

    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val zoneId = remember { ZoneId.systemDefault() }

    val (monthStart, monthEnd) = remember(selectedMonth) { FormatUtils.monthRangeMillis(selectedMonth) }

    val filteredMonthTransactions = remember(allTransactions, monthStart, monthEnd, searchQuery, onlyNeedsReview, accountMap, categoryMap) {
        allTransactions.filter { tx ->
            val inMonth = if (onlyNeedsReview) true else (tx.timestamp in monthStart until monthEnd)
            val reviewMatch = !onlyNeedsReview || tx.needsReview
            val q = searchQuery.trim()
            val queryMatch = if (q.isEmpty()) true else {
                tx.merchant.contains(q, ignoreCase = true) ||
                    tx.note.contains(q, ignoreCase = true) ||
                    (categoryMap[tx.categoryId]?.name?.contains(q, ignoreCase = true) == true) ||
                    (accountMap[tx.accountId]?.name?.contains(q, ignoreCase = true) == true) ||
                    tx.amount.toString().contains(q)
            }
            inMonth && reviewMatch && queryMatch
        }
    }

    val monthIncome = remember(allTransactions, monthStart, monthEnd) {
        allTransactions.filter { it.timestamp in monthStart until monthEnd }
            .sumOf { LedgerCalculations.effectiveIncomeBaseAmount(it) }
    }
    val monthExpense = remember(allTransactions, monthStart, monthEnd) {
        allTransactions.filter { it.timestamp in monthStart until monthEnd }
            .sumOf { LedgerCalculations.effectiveExpenseBaseAmount(it) }
    }

    val selectableIds = remember(viewMode, filteredMonthTransactions, selectedCalendarDate, zoneId) {
        when (viewMode) {
            LedgerViewMode.DAILY -> filteredMonthTransactions.map { it.id }
            LedgerViewMode.CALENDAR -> filteredMonthTransactions.filter {
                Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate() == selectedCalendarDate
            }.map { it.id }
            LedgerViewMode.MONTHLY -> emptyList()
        }
    }
    val hasSelectedNeedsReview = remember(selectedIds, filteredMonthTransactions) {
        filteredMonthTransactions.any { it.id in selectedIds && it.needsReview }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedIds.size} selected",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { exitSelectionMode() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        if (selectableIds.isNotEmpty()) {
                            IconButton(onClick = {
                                val allSelected = selectableIds.all { it in selectedIds }
                                selectedIds = if (allSelected) emptySet() else selectableIds.toSet()
                            }) {
                                Icon(Icons.Rounded.SelectAll, contentDescription = "Select all")
                            }
                        }
                        if (hasSelectedNeedsReview && selectedIds.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.markTransactionsReviewed(selectedIds.toList())
                                exitSelectionMode()
                            }) {
                                Icon(Icons.Rounded.DoneAll, contentDescription = "Mark reviewed")
                            }
                        }
                        IconButton(
                            enabled = selectedIds.isNotEmpty(),
                            onClick = { showBulkDeleteDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete selected",
                                tint = if (selectedIds.isNotEmpty()) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                }
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Money Smart", fontWeight = FontWeight.Bold)
                            Text(
                                text = if (isListenerEnabled && settings.geminiApiKey.isNotBlank()) {
                                    "AI auto-capture active (${settings.allowedPackages.size} apps)"
                                } else {
                                    "AI capture setup needed"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        if (viewMode != LedgerViewMode.MONTHLY && filteredMonthTransactions.isNotEmpty()) {
                            LabeledActionButton(
                                icon = Icons.Rounded.Checklist,
                                label = "Select",
                                onClick = { selectionMode = true }
                            )
                        }
                        LabeledActionButton(
                            icon = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                            label = if (searchVisible) "Close" else "Search",
                            onClick = { searchVisible = !searchVisible }
                        )
                        LabeledActionButton(
                            icon = Icons.Rounded.AutoAwesome,
                            label = "AI Setup",
                            tint = if (isListenerEnabled && settings.geminiApiKey.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                WarningAmber
                            },
                            onClick = onOpenAiActivity
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        TopBarAddButton(
                            label = "Add",
                            onClick = onAddTransaction,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        ResponsiveContentBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            maxWidth = if (isWide) 1040.dp else 940.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (searchVisible) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search merchant, category, account, note...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                if (!bannerDismissed && (!isListenerEnabled || settings.geminiApiKey.isBlank() || settings.allowedPackages.isEmpty())) {
                    SetupPromptBanner(
                        hasKey = settings.geminiApiKey.isNotBlank(),
                        hasListener = isListenerEnabled,
                        hasApps = settings.allowedPackages.isNotEmpty(),
                        onClick = if (settings.geminiApiKey.isBlank()) onOpenSettings else onOpenAiActivity,
                        onDismiss = { bannerDismissed = true }
                    )
                }

                if (isWide) {
                    WideMonthAndSummaryBar(
                        yearMonth = selectedMonth,
                        income = monthIncome,
                        expense = monthExpense,
                        baseCurrency = settings.baseCurrency,
                        onPreviousMonth = { viewModel.previousMonth() },
                        onNextMonth = { viewModel.nextMonth() }
                    )
                } else {
                    MonthSelectorBar(
                        yearMonth = selectedMonth,
                        onPreviousMonth = { viewModel.previousMonth() },
                        onNextMonth = { viewModel.nextMonth() }
                    )
                    SummaryStrip(
                        income = monthIncome,
                        expense = monthExpense,
                        baseCurrency = settings.baseCurrency
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = if (isWide) 4.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                        LedgerViewMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = viewMode == mode,
                                onClick = { viewMode = mode },
                                shape = SegmentedButtonDefaults.itemShape(index, LedgerViewMode.entries.size),
                                icon = {
                                    val icon = when (mode) {
                                        LedgerViewMode.DAILY -> Icons.AutoMirrored.Rounded.ViewList
                                        LedgerViewMode.MONTHLY -> Icons.Rounded.ViewAgenda
                                        LedgerViewMode.CALENDAR -> Icons.Rounded.CalendarMonth
                                    }
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            ) {
                                Text(mode.label, maxLines = 1, fontSize = 13.sp)
                            }
                        }
                    }

                    if (needsReviewCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = onlyNeedsReview,
                            onClick = { onlyNeedsReview = !onlyNeedsReview },
                            label = { Text("Review ($needsReviewCount)") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = WarningAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                when (viewMode) {
                    LedgerViewMode.DAILY -> DailyTransactionsList(
                        transactions = filteredMonthTransactions,
                        accountMap = accountMap,
                        categoryMap = categoryMap,
                        baseCurrency = settings.baseCurrency,
                        zoneId = zoneId,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onAddTransaction = onAddTransaction,
                        onRowClick = { txId ->
                            if (selectionMode) toggleSelection(txId) else onEditTransaction(txId)
                        },
                        onRowLongClick = { txId -> enterSelectionWith(txId) }
                    )

                    LedgerViewMode.MONTHLY -> MonthlyOverviewList(
                        year = selectedMonth.year,
                        allTransactions = allTransactions,
                        baseCurrency = settings.baseCurrency,
                        zoneId = zoneId,
                        isWide = isWide,
                        onSelectMonth = { ym ->
                            viewModel.setMonth(ym)
                            viewMode = LedgerViewMode.DAILY
                        }
                    )

                    LedgerViewMode.CALENDAR -> CalendarTransactionsView(
                        yearMonth = selectedMonth,
                        selectedDate = selectedCalendarDate,
                        onSelectDate = { selectedCalendarDate = it },
                        monthTransactions = filteredMonthTransactions,
                        accountMap = accountMap,
                        categoryMap = categoryMap,
                        baseCurrency = settings.baseCurrency,
                        zoneId = zoneId,
                        isWide = isWide,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onAddTransaction = onAddTransaction,
                        onRowClick = { txId ->
                            if (selectionMode) toggleSelection(txId) else onEditTransaction(txId)
                        },
                        onRowLongClick = { txId -> enterSelectionWith(txId) }
                    )
                }
            }
        }
    }

    if (showBulkDeleteDialog) {
        val idsToDelete = selectedIds.toList()
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Delete ${idsToDelete.size} transactions?") },
            text = { Text("The selected transactions will be removed from your balances and stats.") },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showBulkDeleteDialog = false
                        viewModel.deleteTransactions(idsToDelete)
                        exitSelectionMode()
                        onBulkDeleted(idsToDelete)
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SetupPromptBanner(
    hasKey: Boolean,
    hasListener: Boolean,
    hasApps: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val message = when {
        !hasKey -> "Add your free Gemini API key in Settings to activate AI payment reading."
        !hasListener -> "Enable Notification Access so Money Smart can read payment alerts."
        !hasApps -> "Select your banking & e-wallet apps to start auto-recording expenses."
        else -> ""
    }
    if (message.isBlank()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(if (!hasKey) "Open Settings" else "Setup Now")
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss setup banner",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun WideMonthAndSummaryBar(
    yearMonth: YearMonth,
    income: Double,
    expense: Double,
    baseCurrency: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val incomeColor = if (isDark) IncomeGreenDark else IncomeGreen
    val expenseColor = if (isDark) ExpenseRedDark else ExpenseRed
    val net = income - expense

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousMonth, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
                }
                Text(
                    text = FormatUtils.formatYearMonth(yearMonth),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                IconButton(onClick = onNextMonth, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryMetric("Income", FormatUtils.formatMoney(income, baseCurrency), incomeColor)
                SummaryMetric("Expenses", FormatUtils.formatMoney(expense, baseCurrency), expenseColor)
                SummaryMetric("Net", FormatUtils.formatMoney(net, baseCurrency), MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun SummaryStrip(
    income: Double,
    expense: Double,
    baseCurrency: String
) {
    val isDark = isSystemInDarkTheme()
    val incomeColor = if (isDark) IncomeGreenDark else IncomeGreen
    val expenseColor = if (isDark) ExpenseRedDark else ExpenseRed
    val net = income - expense

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryMetric("Income", FormatUtils.formatMoney(income, baseCurrency), incomeColor)
            SummaryMetric("Expenses", FormatUtils.formatMoney(expense, baseCurrency), expenseColor)
            SummaryMetric("Net", FormatUtils.formatMoney(net, baseCurrency), MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DailyTransactionsList(
    transactions: List<TransactionEntity>,
    accountMap: Map<Long, AccountEntity>,
    categoryMap: Map<Long, CategoryEntity>,
    baseCurrency: String,
    zoneId: ZoneId,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onAddTransaction: () -> Unit,
    onRowClick: (Long) -> Unit,
    onRowLongClick: (Long) -> Unit
) {
    if (transactions.isEmpty()) {
        EmptyStateCard(
            title = "No transactions yet",
            subtitle = "Add an expense or income manually, or enable AI notification capture for your banking & e-wallet apps.",
            actionLabel = "Add Transaction",
            onAction = onAddTransaction
        )
        return
    }

    val grouped = remember(transactions, zoneId) {
        transactions.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate()
        }.toSortedMap(compareByDescending { it })
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        grouped.forEach { (date, dayTxs) ->
            val dayIncome = dayTxs.sumOf { LedgerCalculations.effectiveIncomeBaseAmount(it) }
            val dayExpense = dayTxs.sumOf { LedgerCalculations.effectiveExpenseBaseAmount(it) }
            item(key = "header_$date") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = FormatUtils.formatDayHeader(dayTxs.first().timestamp, zoneId),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (dayIncome > 0) {
                                Text(
                                    text = "+${FormatUtils.formatMoney(dayIncome, baseCurrency)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = IncomeGreen
                                )
                            }
                            if (dayExpense > 0) {
                                Text(
                                    text = "-${FormatUtils.formatMoney(dayExpense, baseCurrency)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ExpenseRed
                                )
                            }
                        }
                    }
                }
            }
            items(dayTxs, key = { it.id }) { tx ->
                TransactionRow(
                    transaction = tx,
                    account = accountMap[tx.accountId],
                    toAccount = tx.toAccountId?.let { accountMap[it] },
                    category = tx.categoryId?.let { categoryMap[it] },
                    baseCurrency = baseCurrency,
                    onClick = { onRowClick(tx.id) },
                    selectionMode = selectionMode,
                    isSelected = tx.id in selectedIds,
                    onLongClick = { onRowLongClick(tx.id) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 70.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun MonthlyOverviewList(
    year: Int,
    allTransactions: List<TransactionEntity>,
    baseCurrency: String,
    zoneId: ZoneId,
    isWide: Boolean,
    onSelectMonth: (YearMonth) -> Unit
) {
    val months = remember(year) { (12 downTo 1).map { YearMonth.of(year, it) } }
    val monthRows = remember(months, isWide) {
        if (isWide) months.chunked(2) else months.chunked(1)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(monthRows) { rowMonths ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowMonths.forEach { ym ->
                    val (start, end) = FormatUtils.monthRangeMillis(ym, zoneId)
                    val monthTxs = allTransactions.filter { it.timestamp in start until end }
                    val inc = monthTxs.sumOf { LedgerCalculations.effectiveIncomeBaseAmount(it) }
                    val exp = monthTxs.sumOf { LedgerCalculations.effectiveExpenseBaseAmount(it) }
                    val net = inc - exp

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectMonth(ym) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = FormatUtils.formatYearMonth(ym),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${monthTxs.size} transactions",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "+${FormatUtils.formatMoney(inc, baseCurrency)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = IncomeGreen
                                )
                                Text(
                                    text = "-${FormatUtils.formatMoney(exp, baseCurrency)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ExpenseRed
                                )
                                Text(
                                    text = "Net ${FormatUtils.formatMoney(net, baseCurrency)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                if (isWide && rowMonths.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CalendarTransactionsView(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    monthTransactions: List<TransactionEntity>,
    accountMap: Map<Long, AccountEntity>,
    categoryMap: Map<Long, CategoryEntity>,
    baseCurrency: String,
    zoneId: ZoneId,
    isWide: Boolean,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onAddTransaction: () -> Unit,
    onRowClick: (Long) -> Unit,
    onRowLongClick: (Long) -> Unit
) {
    val byDate = remember(monthTransactions, zoneId) {
        monthTransactions.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate()
        }
    }

    val firstDayOffset = yearMonth.atDay(1).dayOfWeek.value % 7 // Sun=0..Sat=6
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = ((firstDayOffset + daysInMonth + 6) / 7) * 7
    val selectedDayTransactions = byDate[selectedDate].orEmpty()
    val selectedDayLabel = remember(selectedDate, zoneId) {
        FormatUtils.formatDayHeader(selectedDate.atStartOfDay(zoneId).toInstant().toEpochMilli(), zoneId)
    }

    @Composable
    fun CalendarGridBlock(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .then(if (isWide) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
            ) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { dayName ->
                    Text(
                        text = dayName,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val rows = totalCells / 7
            val cellHeight = if (isWide) 46.dp else 58.dp
            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val cellIndex = r * 7 + c
                        val dayNum = cellIndex - firstDayOffset + 1
                        if (dayNum in 1..daysInMonth) {
                            val date = yearMonth.atDay(dayNum)
                            val dayTxs = byDate[date].orEmpty()
                            val dayExp = dayTxs.sumOf { LedgerCalculations.effectiveExpenseBaseAmount(it) }
                            val dayInc = dayTxs.sumOf { LedgerCalculations.effectiveIncomeBaseAmount(it) }
                            val isSelected = date == selectedDate

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(cellHeight)
                                    .padding(1.5.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.75.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectDate(date) }
                                    .padding(horizontal = 3.dp, vertical = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        fontSize = 12.5.sp,
                                        lineHeight = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    if (isWide) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (dayInc > 0) {
                                                Text(
                                                    text = "+${dayInc.toInt()}",
                                                    fontSize = 10.sp,
                                                    lineHeight = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = IncomeGreen,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Clip
                                                )
                                            }
                                            if (dayExp > 0) {
                                                Text(
                                                    text = "-${dayExp.toInt()}",
                                                    fontSize = 10.sp,
                                                    lineHeight = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = ExpenseRed,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Clip
                                                )
                                            }
                                        }
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            if (dayInc > 0) {
                                                Text(
                                                    text = "+${dayInc.toInt()}",
                                                    fontSize = 10.sp,
                                                    lineHeight = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = IncomeGreen,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Clip
                                                )
                                            }
                                            if (dayExp > 0) {
                                                Text(
                                                    text = "-${dayExp.toInt()}",
                                                    fontSize = 10.sp,
                                                    lineHeight = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = ExpenseRed,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Clip
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(cellHeight)
                                    .padding(1.5.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (isWide) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp)
        ) {
            CalendarGridBlock(
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight()
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedDayLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${selectedDayTransactions.size} item(s)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (selectedDayTransactions.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No transactions on $selectedDayLabel",
                            subtitle = "Pick another day on the calendar or add a new entry.",
                            actionLabel = "Add Transaction",
                            onAction = onAddTransaction
                        )
                    }
                } else {
                    items(selectedDayTransactions, key = { it.id }) { tx ->
                        TransactionRow(
                            transaction = tx,
                            account = accountMap[tx.accountId],
                            toAccount = tx.toAccountId?.let { accountMap[it] },
                            category = tx.categoryId?.let { categoryMap[it] },
                            baseCurrency = baseCurrency,
                            onClick = { onRowClick(tx.id) },
                            selectionMode = selectionMode,
                            isSelected = tx.id in selectedIds,
                            onLongClick = { onRowLongClick(tx.id) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 70.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                CalendarGridBlock()
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }

            if (selectedDayTransactions.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No transactions on $selectedDayLabel",
                        subtitle = "Tap another day above or add a transaction.",
                        actionLabel = "Add Transaction",
                        onAction = onAddTransaction
                    )
                }
            } else {
                items(selectedDayTransactions, key = { it.id }) { tx ->
                    TransactionRow(
                        transaction = tx,
                        account = accountMap[tx.accountId],
                        toAccount = tx.toAccountId?.let { accountMap[it] },
                        category = tx.categoryId?.let { categoryMap[it] },
                        baseCurrency = baseCurrency,
                        onClick = { onRowClick(tx.id) },
                        selectionMode = selectionMode,
                        isSelected = tx.id in selectedIds,
                        onLongClick = { onRowLongClick(tx.id) }
                    )
                }
            }
        }
    }
}
