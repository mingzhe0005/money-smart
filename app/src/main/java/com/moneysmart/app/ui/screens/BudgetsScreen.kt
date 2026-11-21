package com.moneysmart.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneysmart.app.data.local.entity.BudgetEntity
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.CategoryType
import com.moneysmart.app.ui.components.EmptyStateCard
import com.moneysmart.app.ui.components.MonthSelectorBar
import com.moneysmart.app.ui.theme.ExpenseRed
import com.moneysmart.app.ui.theme.IncomeGreen
import com.moneysmart.app.ui.theme.WarningAmber
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import com.moneysmart.app.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    viewModel: MoneySmartViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val budgetProgress by viewModel.budgetProgressForSelectedMonth.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    val expenseCategories = remember(categories) {
        categories.filter { it.type == CategoryType.EXPENSE && it.parentId == null }
    }

    var showDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<BudgetEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monthly Budgets", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingBudget = null
                    showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add budget")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MonthSelectorBar(
                yearMonth = selectedMonth,
                onPreviousMonth = { viewModel.previousMonth() },
                onNextMonth = { viewModel.nextMonth() }
            )

            if (budgetProgress.isEmpty()) {
                EmptyStateCard(
                    title = "No budgets set for ${FormatUtils.formatYearMonth(selectedMonth)}",
                    subtitle = "Tap + to create an overall monthly budget or category spending limits."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(budgetProgress, key = { it.budget.id }) { item ->
                        val ratio = item.progressRatio
                        val pct = (ratio * 100f).toInt()
                        val barColor = when {
                            ratio >= 1f -> ExpenseRed
                            ratio >= (item.budget.alertThresholdPercent / 100f) -> WarningAmber
                            else -> IncomeGreen
                        }
                        val remaining = item.budget.limitAmount - item.spentAmount

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editingBudget = item.budget
                                    showDialog = true
                                }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.categoryName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "$pct%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = barColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { ratio.coerceIn(0f, 1f) },
                                    color = barColor,
                                    trackColor = barColor.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Spent ${FormatUtils.formatMoney(item.spentAmount, settings.baseCurrency)} / ${FormatUtils.formatMoney(item.budget.limitAmount, settings.baseCurrency)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (remaining >= 0) {
                                            "${FormatUtils.formatMoney(remaining, settings.baseCurrency)} left"
                                        } else {
                                            "Over by ${FormatUtils.formatMoney(-remaining, settings.baseCurrency)}"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (remaining >= 0) MaterialTheme.colorScheme.onSurface else ExpenseRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        BudgetEditorDialog(
            initial = editingBudget,
            yearMonthKey = FormatUtils.yearMonthKey(selectedMonth),
            baseCurrency = settings.baseCurrency,
            expenseCategories = expenseCategories,
            onDismiss = { showDialog = false },
            onSave = { budget ->
                viewModel.saveBudget(budget)
                showDialog = false
            },
            onDelete = { id ->
                viewModel.deleteBudget(id)
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetEditorDialog(
    initial: BudgetEntity?,
    yearMonthKey: String,
    baseCurrency: String,
    expenseCategories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (BudgetEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    var selectedCategoryId by remember { mutableStateOf(initial?.categoryId) }
    var limitText by remember { mutableStateOf(initial?.limitAmount?.toString() ?: "") }
    var thresholdText by remember { mutableStateOf((initial?.alertThresholdPercent ?: 80).toString()) }
    var catExpanded by remember { mutableStateOf(false) }

    val selectedLabel = selectedCategoryId?.let { id ->
        expenseCategories.firstOrNull { it.id == id }?.name
    } ?: "Overall Monthly Budget"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Set Budget" else "Edit Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = !catExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Budget Scope") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Overall Monthly Budget") },
                            onClick = {
                                selectedCategoryId = null
                                catExpanded = false
                            }
                        )
                        expenseCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedCategoryId = cat.id
                                    catExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Monthly Limit ($baseCurrency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it },
                    label = { Text("Alert Threshold (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val limit = limitText.toDoubleOrNull()
            TextButton(
                enabled = limit != null && limit > 0.0,
                onClick = {
                    val threshold = (thresholdText.toIntOrNull() ?: 80).coerceIn(10, 99)
                    onSave(
                        BudgetEntity(
                            id = initial?.id ?: 0L,
                            categoryId = selectedCategoryId,
                            yearMonth = initial?.yearMonth ?: yearMonthKey,
                            limitAmount = limit!!,
                            alertThresholdPercent = threshold,
                            alertSent80 = false,
                            alertSent100 = false,
                            createdAt = initial?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        onClick = { onDelete(initial.id) }
                    ) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
