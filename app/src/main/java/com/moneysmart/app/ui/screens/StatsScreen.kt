package com.moneysmart.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.data.repository.LedgerCalculations
import com.moneysmart.app.ui.components.EmptyStateCard
import com.moneysmart.app.ui.components.MonthSelectorBar
import com.moneysmart.app.ui.theme.ExpenseRed
import com.moneysmart.app.ui.theme.IncomeGreen
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import com.moneysmart.app.util.FormatUtils
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

private enum class StatsPeriod(val label: String) {
    MONTH("Monthly"),
    YEAR("Yearly")
}

private enum class StatsTypeFilter(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    BOTH("Both")
}

private data class CategorySlice(
    val key: String,
    val name: String,
    val color: Color,
    val amount: Double,
    val percent: Float
)

private data class DualBarEntry(
    val label: String,
    val income: Double,
    val expense: Double
)

private val statsChartPalette = listOf(
    Color(0xFFEF4444), // Red
    Color(0xFFF59E0B), // Amber
    Color(0xFF10B981), // Emerald
    Color(0xFF0EA5E9), // Sky Blue
    Color(0xFF8B5CF6), // Violet
    Color(0xFFEC4899), // Pink
    Color(0xFF14B8A6), // Teal
    Color(0xFF6366F1), // Indigo
    Color(0xFFF97316), // Orange
    Color(0xFF06B6D4), // Cyan
    Color(0xFFF43F5E), // Rose
    Color(0xFF84CC16), // Lime
    Color(0xFFA855F7), // Purple
    Color(0xFF64748B)  // Slate
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: MoneySmartViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()

    var period by remember { mutableStateOf(StatsPeriod.MONTH) }
    var typeFilter by remember { mutableStateOf(StatsTypeFilter.EXPENSE) }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val zoneId = remember { ZoneId.systemDefault() }

    val (startMillis, endMillis) = remember(period, selectedMonth) {
        if (period == StatsPeriod.MONTH) {
            FormatUtils.monthRangeMillis(selectedMonth, zoneId)
        } else {
            FormatUtils.yearRangeMillis(selectedMonth.year, zoneId)
        }
    }

    val periodTxsAll = remember(transactions, startMillis, endMillis) {
        transactions.filter { it.timestamp in startMillis until endMillis }
    }

    val totalExpense = remember(periodTxsAll) {
        periodTxsAll.sumOf { LedgerCalculations.effectiveExpenseBaseAmount(it) }
    }
    val totalIncome = remember(periodTxsAll) {
        periodTxsAll.sumOf { LedgerCalculations.effectiveIncomeBaseAmount(it) }
    }

    fun buildSlicesFor(txType: TransactionType, prefix: String): List<CategorySlice> {
        val relevant = periodTxsAll.filter { tx ->
            tx.type == txType && when (txType) {
                TransactionType.EXPENSE -> LedgerCalculations.effectiveExpenseBaseAmount(tx) > 0.0
                TransactionType.INCOME -> LedgerCalculations.effectiveIncomeBaseAmount(tx) > 0.0
                TransactionType.TRANSFER -> false
            }
        }
        val total = relevant.sumOf { tx ->
            if (txType == TransactionType.EXPENSE) LedgerCalculations.effectiveExpenseBaseAmount(tx)
            else LedgerCalculations.effectiveIncomeBaseAmount(tx)
        }
        if (total <= 0.0) return emptyList()

        val grouped = relevant.groupBy { tx ->
            val cat = tx.categoryId?.let { categoryMap[it] }
            val rootCat = cat?.parentId?.let { categoryMap[it] } ?: cat
            rootCat?.id
        }
        val sorted = grouped.entries.map { (rootCatId, list) ->
            val cat: CategoryEntity? = rootCatId?.let { categoryMap[it] }
            val sum = list.sumOf { tx ->
                if (txType == TransactionType.EXPENSE) LedgerCalculations.effectiveExpenseBaseAmount(tx)
                else LedgerCalculations.effectiveIncomeBaseAmount(tx)
            }
            Triple(rootCatId, cat?.name ?: "Uncategorized", sum)
        }.sortedByDescending { it.third }

        return sorted.mapIndexed { index, (rootCatId, name, sum) ->
            val color = if (txType == TransactionType.INCOME) {
                statsChartPalette[(index + 2) % statsChartPalette.size]
            } else {
                statsChartPalette[index % statsChartPalette.size]
            }
            CategorySlice(
                key = "${prefix}_${rootCatId ?: -1L}",
                name = name,
                color = color,
                amount = sum,
                percent = (sum / total).toFloat()
            )
        }
    }

    val expenseSlices = remember(periodTxsAll, categoryMap) { buildSlicesFor(TransactionType.EXPENSE, "exp") }
    val incomeSlices = remember(periodTxsAll, categoryMap) { buildSlicesFor(TransactionType.INCOME, "inc") }

    val donutSlices = remember(typeFilter, expenseSlices, incomeSlices, totalExpense, totalIncome) {
        when (typeFilter) {
            StatsTypeFilter.EXPENSE -> expenseSlices
            StatsTypeFilter.INCOME -> incomeSlices
            StatsTypeFilter.BOTH -> {
                val sumBoth = totalExpense + totalIncome
                if (sumBoth <= 0.0) emptyList()
                else listOf(
                    CategorySlice(
                        key = "both_inc",
                        name = "Income",
                        color = IncomeGreen,
                        amount = totalIncome,
                        percent = (totalIncome / sumBoth).toFloat()
                    ),
                    CategorySlice(
                        key = "both_exp",
                        name = "Expense",
                        color = ExpenseRed,
                        amount = totalExpense,
                        percent = (totalExpense / sumBoth).toFloat()
                    )
                ).filter { it.amount > 0.0 }
            }
        }
    }

    val dualBars = remember(transactions, period, selectedMonth, periodTxsAll, zoneId) {
        if (period == StatsPeriod.YEAR) {
            (1..12).map { m ->
                val ym = YearMonth.of(selectedMonth.year, m)
                val (s, e) = FormatUtils.monthRangeMillis(ym, zoneId)
                val txs = transactions.filter { it.timestamp in s until e }
                DualBarEntry(
                    label = ym.month.name.take(3),
                    income = txs.sumOf { LedgerCalculations.effectiveIncomeBaseAmount(it) },
                    expense = txs.sumOf { LedgerCalculations.effectiveExpenseBaseAmount(it) }
                )
            }
        } else {
            val daysInMonth = selectedMonth.lengthOfMonth()
            val buckets = listOf(1..7, 8..14, 15..21, 22..daysInMonth)
            buckets.map { range ->
                val txs = periodTxsAll.filter { tx ->
                    val dom = Instant.ofEpochMilli(tx.timestamp).atZone(zoneId).dayOfMonth
                    dom in range
                }
                DualBarEntry(
                    label = "${range.first}-${range.last}",
                    income = txs.sumOf { LedgerCalculations.effectiveIncomeBaseAmount(it) },
                    expense = txs.sumOf { LedgerCalculations.effectiveExpenseBaseAmount(it) }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Spending Analytics", fontWeight = FontWeight.Bold) })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                if (period == StatsPeriod.MONTH) {
                    MonthSelectorBar(
                        yearMonth = selectedMonth,
                        onPreviousMonth = { viewModel.previousMonth() },
                        onNextMonth = { viewModel.nextMonth() }
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.setMonth(selectedMonth.minusYears(1)) }) {
                                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous year")
                            }
                            Text(
                                text = selectedMonth.year.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = { viewModel.setMonth(selectedMonth.plusYears(1)) }) {
                                Icon(Icons.Rounded.ChevronRight, contentDescription = "Next year")
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        StatsPeriod.entries.forEachIndexed { idx, p ->
                            SegmentedButton(
                                selected = period == p,
                                onClick = { period = p },
                                shape = SegmentedButtonDefaults.itemShape(idx, StatsPeriod.entries.size)
                            ) {
                                Text(p.label, fontSize = 12.sp)
                            }
                        }
                    }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        StatsTypeFilter.entries.forEachIndexed { idx, tf ->
                            SegmentedButton(
                                selected = typeFilter == tf,
                                onClick = { typeFilter = tf },
                                shape = SegmentedButtonDefaults.itemShape(idx, StatsTypeFilter.entries.size)
                            ) {
                                Text(tf.label, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (donutSlices.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No ${typeFilter.label.lowercase()} data",
                        subtitle = "Transactions recorded for this period will appear as charts here."
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.size(195.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                DonutChart(
                                    slices = donutSlices,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val centerLabel = when (typeFilter) {
                                        StatsTypeFilter.EXPENSE -> "Total Expense"
                                        StatsTypeFilter.INCOME -> "Total Income"
                                        StatsTypeFilter.BOTH -> "Net Savings"
                                    }
                                    val centerVal = when (typeFilter) {
                                        StatsTypeFilter.EXPENSE -> totalExpense
                                        StatsTypeFilter.INCOME -> totalIncome
                                        StatsTypeFilter.BOTH -> totalIncome - totalExpense
                                    }
                                    Text(
                                        text = centerLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = FormatUtils.formatMoney(centerVal, settings.baseCurrency),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (typeFilter == StatsTypeFilter.BOTH) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Income", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            text = FormatUtils.formatMoney(totalIncome, settings.baseCurrency),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = IncomeGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Expense", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            text = FormatUtils.formatMoney(totalExpense, settings.baseCurrency),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = ExpenseRed,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (period == StatsPeriod.YEAR) "Monthly Trend" else "Weekly Trend",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (typeFilter == StatsTypeFilter.BOTH) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        LegendDot("Income", IncomeGreen)
                                        LegendDot("Expense", ExpenseRed)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            TrendBarChart(
                                bars = dualBars,
                                filter = typeFilter,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (typeFilter == StatsTypeFilter.EXPENSE || typeFilter == StatsTypeFilter.BOTH) {
                    if (typeFilter == StatsTypeFilter.BOTH && expenseSlices.isNotEmpty()) {
                        item {
                            Text(
                                text = "Expenses by Category",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                    items(expenseSlices, key = { it.key }) { slice ->
                        CategorySliceCard(slice, settings.baseCurrency)
                    }
                }

                if (typeFilter == StatsTypeFilter.INCOME || typeFilter == StatsTypeFilter.BOTH) {
                    if (typeFilter == StatsTypeFilter.BOTH && incomeSlices.isNotEmpty()) {
                        item {
                            Text(
                                text = "Income by Category",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)
                            )
                        }
                    }
                    items(incomeSlices, key = { it.key }) { slice ->
                        CategorySliceCard(slice, settings.baseCurrency)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CategorySliceCard(slice: CategorySlice, baseCurrency: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(slice.color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = slice.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "${FormatUtils.formatMoney(slice.amount, baseCurrency)} (${(slice.percent * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { slice.percent.coerceIn(0f, 1f) },
                color = slice.color,
                trackColor = slice.color.copy(alpha = 0.16f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun DonutChart(
    slices: List<CategorySlice>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.padding(12.dp)) {
        val strokeWidth = 34.dp.toPx()
        val diameter = minOf(size.width, size.height) - strokeWidth
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)
        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = (slice.percent * 360f).coerceAtLeast(1f)
            drawArc(
                color = slice.color,
                startAngle = startAngle,
                sweepAngle = if (slices.size > 1) (sweep - 1.5f).coerceAtLeast(0.5f) else sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun TrendBarChart(
    bars: List<DualBarEntry>,
    filter: StatsTypeFilter,
    modifier: Modifier = Modifier
) {
    val maxVal = when (filter) {
        StatsTypeFilter.EXPENSE -> bars.maxOfOrNull { it.expense } ?: 0.0
        StatsTypeFilter.INCOME -> bars.maxOfOrNull { it.income } ?: 0.0
        StatsTypeFilter.BOTH -> bars.maxOfOrNull { maxOf(it.income, it.expense) } ?: 0.0
    }.coerceAtLeast(1.0)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEach { entry ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (filter == StatsTypeFilter.BOTH) {
                        val incRatio = (entry.income / maxVal).toFloat().coerceIn(0f, 1f)
                        val expRatio = (entry.expense / maxVal).toFloat().coerceIn(0f, 1f)
                        val barW = if (bars.size > 6) 6.dp else 14.dp
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(barW)
                                    .height((90f * incRatio).coerceAtLeast(if (entry.income > 0) 4f else 2f).dp)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(if (entry.income > 0) IncomeGreen else IncomeGreen.copy(alpha = 0.15f))
                            )
                            Box(
                                modifier = Modifier
                                    .width(barW)
                                    .height((90f * expRatio).coerceAtLeast(if (entry.expense > 0) 4f else 2f).dp)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(if (entry.expense > 0) ExpenseRed else ExpenseRed.copy(alpha = 0.15f))
                            )
                        }
                    } else {
                        val value = if (filter == StatsTypeFilter.INCOME) entry.income else entry.expense
                        val color = if (filter == StatsTypeFilter.INCOME) IncomeGreen else ExpenseRed
                        val ratio = (value / maxVal).toFloat().coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .width(if (bars.size > 6) 14.dp else 28.dp)
                                .height((90f * ratio).coerceAtLeast(if (value > 0) 4f else 2f).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (value > 0) color else color.copy(alpha = 0.15f))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.label,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
