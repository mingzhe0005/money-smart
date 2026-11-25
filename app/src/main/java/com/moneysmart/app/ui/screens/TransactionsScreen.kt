package com.moneysmart.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.moneysmart.app.ui.components.SoftElevatedCard
import com.moneysmart.app.ui.components.TransactionRow
import com.moneysmart.app.ui.components.WarmCurvedHeroHeader
import com.moneysmart.app.ui.components.WarmSegmentedPillBar
import com.moneysmart.app.ui.components.rememberIsWideScreen
import com.moneysmart.app.ui.theme.CoralAccent
import com.moneysmart.app.ui.theme.CoralSoft
import com.moneysmart.app.ui.theme.EspressoInk
import com.moneysmart.app.ui.theme.ExpenseRed
import com.moneysmart.app.ui.theme.ExpenseRedDark
import com.moneysmart.app.ui.theme.IncomeGreen
import com.moneysmart.app.ui.theme.IncomeGreenDark
import com.moneysmart.app.ui.theme.PeachSoft
import com.moneysmart.app.ui.theme.PeachTint
import com.moneysmart.app.ui.theme.TrackBeige
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
        LedgerCalculations.effectiveTotalExpensesBase(
            allTransactions.filter { it.timestamp in monthStart until monthEnd }
        )
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (selectionMode) {
                Surface(
                    color = EspressoInk,
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { exitSelectionMode() }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Cancel selection", tint = Color.White)
                            }
                            Text(
                                text = "${selectedIds.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectableIds.isNotEmpty()) {
                                IconButton(onClick = {
                                    val allSelected = selectableIds.all { it in selectedIds }
                                    selectedIds = if (allSelected) emptySet() else selectableIds.toSet()
                                }) {
                                    Icon(Icons.Rounded.SelectAll, contentDescription = "Select all", tint = Color.White)
                                }
                            }
                            if (hasSelectedNeedsReview && selectedIds.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.markTransactionsReviewed(selectedIds.toList())
                                    exitSelectionMode()
                                }) {
                                    Icon(Icons.Rounded.DoneAll, contentDescription = "Mark reviewed", tint = Color.White)
                                }
                            }
                            IconButton(
                                enabled = selectedIds.isNotEmpty(),
                                onClick = { showBulkDeleteDialog = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete selected",
                                    tint = if (selectedIds.isNotEmpty()) CoralAccent else Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            } else {
                WarmCurvedHeroHeader(
                    title = "Money Smart",
                    subtitle = when {
                        !settings.aiCaptureEnabled -> "Personal finance tracker"
                        isListenerEnabled && settings.geminiApiKey.isNotBlank() ->
                            "AI auto-capture (${settings.allowedPackages.size} apps)"
                        else -> "AI capture setup needed"
                    },
                    heroColor = PeachSoft,
                    maxWidth = if (isWide) 1040.dp else 940.dp,
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
                        if (settings.aiCaptureEnabled) {
                            LabeledActionButton(
                                icon = Icons.Rounded.AutoAwesome,
                                label = "AI Setup",
                                tint = if (isListenerEnabled && settings.geminiApiKey.isNotBlank()) {
                                    IncomeGreen
                                } else {
                                    CoralAccent
                                },
                                onClick = onOpenAiActivity
                            )
                        }
                    }
                )
            }

        ResponsiveContentBox(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            maxWidth = if (isWide) 1040.dp else 940.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (searchVisible) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search merchant, category, account, note...") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                if (settings.aiCaptureEnabled &&
                    !bannerDismissed &&
                    (!isListenerEnabled || settings.geminiApiKey.isBlank() || settings.allowedPackages.isEmpty())
                ) {
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
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    WarmSegmentedPillBar(
                        options = LedgerViewMode.entries.map { it.label },
                        icons = listOf(
                            Icons.AutoMirrored.Rounded.ViewList,
                            Icons.Rounded.ViewAgenda,
                            Icons.Rounded.CalendarMonth
                        ),
                        selectedIndex = viewMode.ordinal,
                        onSelect = { idx -> viewMode = LedgerViewMode.entries[idx] },
                        modifier = Modifier.weight(1f)
                    )

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

        if (!selectionMode) {
            FloatingActionButton(
                onClick = onAddTransaction,
                containerColor = CoralAccent,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 14.dp)
                    .size(54.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add transaction")
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

    val isDark = isSystemInDarkTheme()
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainer else PeachTint,
        border = BorderStroke(1.dp, CoralAccent.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = CoralAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = if (!hasKey) "Settings" else "Setup",
                    color = CoralAccent,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss setup banner",
                    modifier = Modifier.size(15.dp)
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

    SoftElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        cornerRadius = 18.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousMonth, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
                }
                Text(
                    text = FormatUtils.formatYearMonth(yearMonth),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                IconButton(onClick = onNextMonth, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryMetric("Income", FormatUtils.formatMoney(income, baseCurrency), incomeColor)
                SummaryMetric("Expenses", FormatUtils.formatMoney(expense, baseCurrency), expenseColor)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else EspressoInk
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "Net",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                        Text(
                            text = FormatUtils.formatMoney(net, baseCurrency),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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

    SoftElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        cornerRadius = 18.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryMetric(
                label = "Income",
                value = FormatUtils.formatMoney(income, baseCurrency),
                color = incomeColor
            )
            SummaryMetric(
                label = "Expenses",
                value = FormatUtils.formatMoney(expense, baseCurrency),
                color = expenseColor
            )
            SummaryMetric(
                label = "Net",
                value = FormatUtils.formatMoney(net, baseCurrency),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val compactLargeFont = LocalConfiguration.current.screenWidthDp < 390 &&
        LocalDensity.current.fontScale > 1.15f
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = if (compactLargeFont) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
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
        contentPadding = PaddingValues(top = 4.dp, bottom = 84.dp)
    ) {
        grouped.forEach { (date, dayTxs) ->
            val dayIncome = dayTxs.sumOf { LedgerCalculations.effectiveIncomeBaseAmount(it) }
            val dayExpense = LedgerCalculations.effectiveTotalExpensesBase(dayTxs)
            item(key = "header_$date") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = FormatUtils.formatDayHeader(dayTxs.first().timestamp, zoneId),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
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
            item(key = "group_card_$date") {
                GroupedDayTransactionsCard(
                    transactions = dayTxs,
                    accountMap = accountMap,
                    categoryMap = categoryMap,
                    baseCurrency = baseCurrency,
                    selectionMode = selectionMode,
                    selectedIds = selectedIds,
                    onRowClick = onRowClick,
                    onRowLongClick = onRowLongClick
                )
            }
        }
    }
}

@Composable
private fun GroupedDayTransactionsCard(
    transactions: List<TransactionEntity>,
    accountMap: Map<Long, AccountEntity>,
    categoryMap: Map<Long, CategoryEntity>,
    baseCurrency: String,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onRowClick: (Long) -> Unit,
    onRowLongClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    SoftElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        cornerRadius = 18.dp
    ) {
        transactions.forEachIndexed { idx, tx ->
            TransactionRow(
                transaction = tx,
                account = accountMap[tx.accountId],
                toAccount = tx.toAccountId?.let { accountMap[it] },
                category = tx.categoryId?.let { categoryMap[it] },
                baseCurrency = baseCurrency,
                onClick = { onRowClick(tx.id) },
                selectionMode = selectionMode,
                isSelected = tx.id in selectedIds,
                onLongClick = { onRowLongClick(tx.id) },
                grouped = true
            )
            if (idx < transactions.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
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
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(monthRows) { rowMonths ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowMonths.forEach { ym ->
                    val (start, end) = FormatUtils.monthRangeMillis(ym, zoneId)
                    val monthTxs = allTransactions.filter { it.timestamp in start until end }
                    val inc = monthTxs.sumOf { LedgerCalculations.effectiveIncomeBaseAmount(it) }
                    val exp = LedgerCalculations.effectiveTotalExpensesBase(monthTxs)
                    val net = inc - exp

                    SoftElevatedCard(
                        onClick = { onSelectMonth(ym) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        cornerRadius = 18.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = FormatUtils.formatYearMonth(ym),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${monthTxs.size} transactions",
                                    style = MaterialTheme.typography.bodySmall,
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
    val isDark = isSystemInDarkTheme()
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
                    .padding(bottom = 3.dp)
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
                            val dayExp = LedgerCalculations.effectiveTotalExpensesBase(dayTxs)
                            val dayInc = dayTxs.sumOf { LedgerCalculations.effectiveIncomeBaseAmount(it) }
                            val isSelected = date == selectedDate
                            val cellBg = when {
                                isSelected && isDark -> CoralAccent
                                isSelected -> EspressoInk
                                else -> MaterialTheme.colorScheme.surface
                            }
                            val numColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            val incColor = if (isSelected) Color(0xFF9DF0D2) else IncomeGreen
                            val expColor = if (isSelected) Color(0xFFFFB49E) else ExpenseRed

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(cellHeight)
                                    .padding(1.5.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(cellBg)
                                    .border(
                                        width = if (isSelected) 0.dp else 0.75.dp,
                                        color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(10.dp)
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
                                        color = numColor,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
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
                                                    color = incColor,
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
                                                    color = expColor,
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
                                                    color = incColor,
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
                                                    color = expColor,
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
                contentPadding = PaddingValues(bottom = 84.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedDayLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = TrackBeige
                        ) {
                            Text(
                                text = "${selectedDayTransactions.size} item(s)",
                                style = MaterialTheme.typography.labelMedium,
                                color = EspressoInk,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
                    item(key = "cal_wide_group") {
                        GroupedDayTransactionsCard(
                            transactions = selectedDayTransactions,
                            accountMap = accountMap,
                            categoryMap = categoryMap,
                            baseCurrency = baseCurrency,
                            selectionMode = selectionMode,
                            selectedIds = selectedIds,
                            onRowClick = onRowClick,
                            onRowLongClick = onRowLongClick
                        )
                    }
                }
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 84.dp)) {
            item {
                CalendarGridBlock()
                Spacer(modifier = Modifier.height(6.dp))
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
                item(key = "cal_portrait_group") {
                    GroupedDayTransactionsCard(
                        transactions = selectedDayTransactions,
                        accountMap = accountMap,
                        categoryMap = categoryMap,
                        baseCurrency = baseCurrency,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onRowClick = onRowClick,
                        onRowLongClick = onRowLongClick
                    )
                }
            }
        }
    }
}
