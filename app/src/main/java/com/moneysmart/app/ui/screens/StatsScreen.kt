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
import com.moneysmart.app.data.local.entity.TransactionType
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

private data class CategorySlice(
    val categoryId: Long?,
    val name: String,
    val color: Color,
    val amount: Double,
    val percent: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: MoneySmartViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()

    var period by remember { mutableStateOf(StatsPeriod.MONTH) }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val zoneId = remember { ZoneId.systemDefault() }

    val (startMillis, endMillis) = remember(period, selectedMonth) {
        if (period == StatsPeriod.MONTH) {
            FormatUtils.monthRangeMillis(selectedMonth, zoneId)
        } else {
            FormatUtils.yearRangeMillis(selectedMonth.year, zoneId)
        }
    }

    val periodTransactions = remember(transactions, startMillis, endMillis, selectedType) {
        transactions.filter {
            it.timestamp in startMillis until endMillis && it.type == selectedType
        }
    }

    val totalAmount = remember(periodTransactions) {
        periodTransactions.sumOf { it.baseAmount }
    }

    val slices = remember(periodTransactions, categoryMap, totalAmount) {
        if (totalAmount <= 0.0) emptyList()
        else {
            val grouped = periodTransactions.groupBy { tx ->
                val cat = tx.categoryId?.let { categoryMap[it] }
                val rootCat = cat?.parentId?.let { categoryMap[it] } ?: cat
                rootCat?.id
            }
            grouped.map { (rootCatId, list) ->
                val cat: CategoryEntity? = rootCatId?.let { categoryMap[it] }
                val sum = list.sumOf { it.baseAmount }
                CategorySlice(
                    categoryId = rootCatId,
                    name = cat?.name ?: "Uncategorized",
                    color = cat?.let { Color(it.colorHex) } ?: Color(0xFF64748B),
                    amount = sum,
                    percent = (sum / totalAmount).toFloat()
                )
            }.sortedByDescending { it.amount }
        }
    }

    val barData = remember(transactions, period, selectedMonth, selectedType, zoneId) {
        if (period == StatsPeriod.YEAR) {
            (1..12).map { m ->
                val ym = YearMonth.of(selectedMonth.year, m)
                val (s, e) = FormatUtils.monthRangeMillis(ym, zoneId)
                val sum = transactions.filter { it.timestamp in s until e && it.type == selectedType }
                    .sumOf { it.baseAmount }
                ym.month.name.take(3) to sum
            }
        } else {
            val daysInMonth = selectedMonth.lengthOfMonth()
            val buckets = listOf(1..7, 8..14, 15..21, 22..daysInMonth)
            buckets.map { range ->
                val sum = periodTransactions.filter { tx ->
                    val dom = Instant.ofEpochMilli(tx.timestamp).atZone(zoneId).dayOfMonth
                    dom in range
                }.sumOf { it.baseAmount }
                "${range.first}-${range.last}" to sum
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
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
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                            listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEachIndexed { idx, t ->
                                SegmentedButton(
                                    selected = selectedType == t,
                                    onClick = { selectedType = t },
                                    shape = SegmentedButtonDefaults.itemShape(idx, 2)
                                ) {
                                    Text(t.displayName, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (slices.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No ${selectedType.displayName.lowercase()} data",
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
                                modifier = Modifier.size(190.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                DonutChart(
                                    slices = slices,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Total ${selectedType.displayName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = FormatUtils.formatMoney(totalAmount, settings.baseCurrency),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
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
                            Text(
                                text = if (period == StatsPeriod.YEAR) "Monthly Trend" else "Weekly Trend",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TrendBarChart(
                                bars = barData,
                                barColor = if (selectedType == TransactionType.EXPENSE) ExpenseRed else IncomeGreen,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(slices, key = { it.categoryId ?: -1L }) { slice ->
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
                                    text = "${FormatUtils.formatMoney(slice.amount, settings.baseCurrency)} (${(slice.percent * 100).toInt()}%)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { slice.percent.coerceIn(0f, 1f) },
                                color = slice.color,
                                trackColor = slice.color.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }
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
    bars: List<Pair<String, Double>>,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val maxVal = (bars.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEach { (label, value) ->
            val ratio = (value / maxVal).toFloat().coerceIn(0f, 1f)
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
                    Box(
                        modifier = Modifier
                            .width(if (bars.size > 6) 14.dp else 28.dp)
                            .height((90f * ratio).coerceAtLeast(if (value > 0) 4f else 2f).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (value > 0) barColor else barColor.copy(alpha = 0.15f))
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
