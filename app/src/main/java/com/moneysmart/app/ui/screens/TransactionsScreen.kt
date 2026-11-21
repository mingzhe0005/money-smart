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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.notification.PaymentNotificationListenerService
import com.moneysmart.app.ui.components.EmptyStateCard
import com.moneysmart.app.ui.components.MonthSelectorBar
import com.moneysmart.app.ui.components.TransactionRow
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
        allTransactions.filter { it.timestamp in monthStart until monthEnd && it.type == TransactionType.INCOME }
            .sumOf { it.baseAmount }
    }
    val monthExpense = remember(allTransactions, monthStart, monthEnd) {
        allTransactions.filter { it.timestamp in monthStart until monthEnd && it.type == TransactionType.EXPENSE }
            .sumOf { it.baseAmount }
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
                            IconButton(onClick = { selectionMode = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Checklist,
                                    contentDescription = "Select multiple transactions"
                                )
                            }
                        }
                        IconButton(onClick = { searchVisible = !searchVisible }) {
                            Icon(
                                imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = "Search transactions"
                            )
                        }
                        IconButton(onClick = onOpenAiActivity) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "AI Activity & Simulator",
                                tint = if (isListenerEnabled && settings.geminiApiKey.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    WarningAmber
                                }
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(
                    onClick = onAddTransaction,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add transaction")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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

            if (!isListenerEnabled || settings.geminiApiKey.isBlank() || settings.allowedPackages.isEmpty()) {
                SetupPromptBanner(
                    hasKey = settings.geminiApiKey.isNotBlank(),
                    hasListener = isListenerEnabled,
                    hasApps = settings.allowedPackages.isNotEmpty(),
                    onClick = if (settings.geminiApiKey.isBlank()) onOpenSettings else onOpenAiActivity
                )
            }

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
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
                            Text(mode.label, maxLines = 1, fontSize = 12.sp)
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
                    selectionMode = selectionMode,
                    selectedIds = selectedIds,
                    onRowClick = { txId ->
                        if (selectionMode) toggleSelection(txId) else onEditTransaction(txId)
                    },
                    onRowLongClick = { txId -> enterSelectionWith(txId) }
                )
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
    onClick: () -> Unit
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
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
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
    onRowClick: (Long) -> Unit,
    onRowLongClick: (Long) -> Unit
) {
    if (transactions.isEmpty()) {
        EmptyStateCard(
            title = "No transactions yet",
            subtitle = "Tap + to add an expense, or enable AI notification capture for your banking & e-wallet apps."
        )
        return
    }

    val grouped = remember(transactions, zoneId) {
        transactions.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate()
        }.toSortedMap(compareByDescending { it })
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        grouped.forEach { (date, dayTxs) ->
            val dayIncome = dayTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.baseAmount }
            val dayExpense = dayTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.baseAmount }
            item(key = "header_$date") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = FormatUtils.formatDayHeader(dayTxs.first().timestamp, zoneId),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (dayIncome > 0) {
                                Text(
                                    text = "+${FormatUtils.formatMoney(dayIncome, baseCurrency)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = IncomeGreen
                                )
                            }
                            if (dayExpense > 0) {
                                Text(
                                    text = "-${FormatUtils.formatMoney(dayExpense, baseCurrency)}",
                                    style = MaterialTheme.typography.labelMedium,
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
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
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
    onSelectMonth: (YearMonth) -> Unit
) {
    val months = remember(year) { (12 downTo 1).map { YearMonth.of(year, it) } }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(months, key = { it.toString() }) { ym ->
            val (start, end) = FormatUtils.monthRangeMillis(ym, zoneId)
            val monthTxs = allTransactions.filter { it.timestamp in start until end }
            val inc = monthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.baseAmount }
            val exp = monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.baseAmount }
            val net = inc - exp

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectMonth(ym) }
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
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(72.dp)) }
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
    selectionMode: Boolean,
    selectedIds: Set<Long>,
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

    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
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
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                for (r in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val cellIndex = r * 7 + c
                            val dayNum = cellIndex - firstDayOffset + 1
                            if (dayNum in 1..daysInMonth) {
                                val date = yearMonth.atDay(dayNum)
                                val dayTxs = byDate[date].orEmpty()
                                val dayExp = dayTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.baseAmount }
                                val dayInc = dayTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.baseAmount }
                                val isSelected = date == selectedDate

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.5.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onSelectDate(date) }
                                        .padding(3.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            if (dayInc > 0) {
                                                Text(
                                                    text = "+${dayInc.toInt()}",
                                                    fontSize = 9.sp,
                                                    color = IncomeGreen,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Clip
                                                )
                                            }
                                            if (dayExp > 0) {
                                                Text(
                                                    text = "-${dayExp.toInt()}",
                                                    fontSize = 9.sp,
                                                    color = ExpenseRed,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Clip
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        }

        if (selectedDayTransactions.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No transactions on ${selectedDate}",
                    subtitle = "Tap another highlighted day or tap + to add one."
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
