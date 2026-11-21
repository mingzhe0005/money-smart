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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.CategoryType
import com.moneysmart.app.data.local.entity.RecurrenceFrequency
import com.moneysmart.app.data.local.entity.RecurringTransactionEntity
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.ui.components.EmptyStateCard
import com.moneysmart.app.ui.theme.ExpenseRed
import com.moneysmart.app.ui.theme.IncomeGreen
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import com.moneysmart.app.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    viewModel: MoneySmartViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recurringList by viewModel.recurringList.collectAsStateWithLifecycle()
    val accounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    var editingItem by remember { mutableStateOf<RecurringTransactionEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring & Subscriptions", fontWeight = FontWeight.Bold) },
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
                    editingItem = null
                    showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add recurring transaction")
            }
        }
    ) { innerPadding ->
        if (recurringList.isEmpty()) {
            Column(modifier = Modifier.padding(innerPadding)) {
                EmptyStateCard(
                    title = "No recurring transactions",
                    subtitle = "Automate rent, salary, phone plans, or streaming subscriptions here."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(recurringList, key = { it.id }) { item ->
                    val accName = accountMap[item.accountId]?.name ?: "Account"
                    val catName = item.categoryId?.let { categoryMap[it]?.name } ?: item.type.displayName
                    val amountColor = if (item.type == TransactionType.INCOME) IncomeGreen else ExpenseRed

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingItem = item
                                showDialog = true
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Every ${item.intervalCount} ${item.frequency.displayName.lowercase()} · $accName · $catName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Next due: ${FormatUtils.formatDate(item.nextDueMillis)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = FormatUtils.formatMoney(item.amount, item.currency),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = amountColor
                                    )
                                    Switch(
                                        checked = item.isActive,
                                        onCheckedChange = { checked ->
                                            viewModel.saveRecurring(item.copy(isActive = checked))
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { viewModel.executeRecurringNow(item.id) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Text("Record Now")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog && accounts.isNotEmpty()) {
        RecurringEditorDialog(
            initial = editingItem,
            defaultCurrency = settings.baseCurrency,
            accounts = accounts,
            categories = categories,
            onDismiss = { showDialog = false },
            onSave = { rec ->
                viewModel.saveRecurring(rec)
                showDialog = false
            },
            onDelete = { id ->
                viewModel.deleteRecurring(id)
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringEditorDialog(
    initial: RecurringTransactionEntity?,
    defaultCurrency: String,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (RecurringTransactionEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var txType by remember { mutableStateOf(initial?.type ?: TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var currency by remember { mutableStateOf(initial?.currency ?: defaultCurrency) }
    var accountId by remember { mutableStateOf(initial?.accountId ?: accounts.first().id) }
    var frequency by remember { mutableStateOf(initial?.frequency ?: RecurrenceFrequency.MONTHLY) }
    var intervalText by remember { mutableStateOf((initial?.intervalCount ?: 1).toString()) }
    var freqExpanded by remember { mutableStateOf(false) }

    val matchingCategories = remember(categories, txType) {
        val catType = if (txType == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        categories.filter { it.type == catType }
    }
    var categoryId by remember {
        mutableStateOf(initial?.categoryId ?: matchingCategories.firstOrNull()?.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Recurring Rule" else "Edit Recurring Rule") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEachIndexed { idx, t ->
                        SegmentedButton(
                            selected = txType == t,
                            onClick = {
                                txType = t
                                val catType = if (t == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
                                categoryId = categories.firstOrNull { it.type == catType }?.id
                            },
                            shape = SegmentedButtonDefaults.itemShape(idx, 2)
                        ) {
                            Text(t.displayName)
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (e.g., Netflix, Rent, Salary)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                    CurrencyDropdown(
                        selectedCurrency = currency,
                        onSelectCurrency = { currency = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                SimpleDropdownField(
                    label = "Account",
                    selectedLabel = accounts.firstOrNull { it.id == accountId }?.name ?: "Select",
                    options = accounts.map { it.id to it.name },
                    onSelect = { accountId = it }
                )
                SimpleDropdownField(
                    label = "Category",
                    selectedLabel = matchingCategories.firstOrNull { it.id == categoryId }?.name ?: "Select",
                    options = matchingCategories.map { it.id to it.name },
                    onSelect = { categoryId = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it },
                        label = { Text("Every") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                    ExposedDropdownMenuBox(
                        expanded = freqExpanded,
                        onExpandedChange = { freqExpanded = !freqExpanded },
                        modifier = Modifier.weight(1.3f)
                    ) {
                        OutlinedTextField(
                            value = frequency.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Frequency") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(freqExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }) {
                            RecurrenceFrequency.entries.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(f.displayName) },
                                    onClick = {
                                        frequency = f
                                        freqExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val amount = amountText.toDoubleOrNull()
            TextButton(
                enabled = title.isNotBlank() && amount != null && amount > 0.0,
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(
                        RecurringTransactionEntity(
                            id = initial?.id ?: 0L,
                            title = title.trim(),
                            type = txType,
                            amount = amount!!,
                            currency = currency.uppercase(),
                            accountId = accountId,
                            categoryId = categoryId,
                            merchant = title.trim(),
                            frequency = frequency,
                            intervalCount = (intervalText.toIntOrNull() ?: 1).coerceAtLeast(1),
                            nextDueMillis = initial?.nextDueMillis ?: now,
                            isActive = initial?.isActive ?: true,
                            lastExecutedMillis = initial?.lastExecutedMillis,
                            createdAt = initial?.createdAt ?: now
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
