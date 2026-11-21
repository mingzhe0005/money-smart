package com.moneysmart.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneysmart.app.data.local.entity.CategoryType
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionSource
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.data.repository.LedgerCalculations
import com.moneysmart.app.ui.theme.WarningAmber
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import com.moneysmart.app.util.FormatUtils
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionScreen(
    viewModel: MoneySmartViewModel,
    transactionId: Long?,
    initialType: TransactionType = TransactionType.EXPENSE,
    onNavigateBack: () -> Unit,
    onDeleted: (Long) -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val accounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var loadedTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var txType by remember { mutableStateOf(initialType) }
    var amountText by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(settings.baseCurrency) }
    var exchangeRateText by remember { mutableStateOf("1.0") }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var selectedToAccountId by remember { mutableStateOf<Long?>(null) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var merchant by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var timestampMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var needsReview by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(settings.baseCurrency) {
        if (transactionId == null && loadedTx == null) {
            currency = settings.baseCurrency
        }
    }

    LaunchedEffect(transactionId) {
        if (transactionId != null && transactionId > 0L) {
            val existing = viewModel.getTransaction(transactionId)
            if (existing != null) {
                loadedTx = existing
                txType = existing.type
                amountText = existing.amount.toString()
                currency = existing.currency
                exchangeRateText = existing.exchangeRateToBase.toString()
                selectedAccountId = existing.accountId
                selectedToAccountId = existing.toAccountId
                selectedCategoryId = existing.categoryId
                merchant = existing.merchant
                note = existing.note
                timestampMillis = existing.timestamp
                needsReview = existing.needsReview
            }
        }
    }

    LaunchedEffect(accounts) {
        if (selectedAccountId == null && accounts.isNotEmpty()) {
            selectedAccountId = accounts.first().id
        }
        if (txType == TransactionType.TRANSFER && selectedToAccountId == null && accounts.size >= 2) {
            selectedToAccountId = accounts.firstOrNull { it.id != selectedAccountId }?.id
        }
    }

    val filteredCategories = remember(categories, txType) {
        val catType = if (txType == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        val matching = categories.filter { it.type == catType }
        val parents = matching.filter { it.parentId == null }
        val ordered = mutableListOf<Pair<Long, String>>()
        for (p in parents) {
            ordered.add(p.id to p.name)
            matching.filter { it.parentId == p.id }.forEach { child ->
                ordered.add(child.id to "   ↳ ${child.name}")
            }
        }
        ordered
    }

    LaunchedEffect(txType, filteredCategories) {
        if (txType != TransactionType.TRANSFER &&
            filteredCategories.isNotEmpty() &&
            filteredCategories.none { it.first == selectedCategoryId }
        ) {
            selectedCategoryId = filteredCategories.first().first
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (loadedTx == null) "New Transaction" else "Edit Transaction") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (loadedTx != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loadedTx?.source == TransactionSource.AI_NOTIFICATION) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (needsReview) {
                            WarningAmber.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val confPct = ((loadedTx?.aiConfidence ?: 0f) * 100).toInt()
                            Text(
                                text = if (needsReview) {
                                    "AI Needs Review ($confPct% confidence)"
                                } else {
                                    "Recorded by AI ($confPct% confidence)"
                                },
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Verify amount, account, and category below.",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        if (needsReview) {
                            TextButton(onClick = { needsReview = false }) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Approve")
                            }
                        }
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TransactionType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = txType == type,
                        onClick = { txType = type },
                        shape = SegmentedButtonDefaults.itemShape(index, TransactionType.entries.size)
                    ) {
                        Text(type.displayName)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1.7f)
                )
                CurrencyDropdown(
                    selectedCurrency = currency,
                    onSelectCurrency = { newCur ->
                        currency = newCur
                        scope.launch {
                            val rate = viewModel.getExchangeRateToBase(newCur, settings.baseCurrency)
                            exchangeRateText = String.format(java.util.Locale.US, "%.4f", rate)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (!currency.equals(settings.baseCurrency, ignoreCase = true)) {
                val parsedAmt = amountText.toDoubleOrNull() ?: 0.0
                val parsedRate = exchangeRateText.toDoubleOrNull() ?: 1.0
                val basePreview = LedgerCalculations.computeBaseAmount(parsedAmt, parsedRate)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = exchangeRateText,
                        onValueChange = { exchangeRateText = it },
                        label = { Text("1 $currency = ? ${settings.baseCurrency}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "≈ ${FormatUtils.formatMoney(basePreview, settings.baseCurrency)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            SimpleDropdownField(
                label = if (txType == TransactionType.TRANSFER) "From Account" else "Account",
                selectedLabel = accounts.firstOrNull { it.id == selectedAccountId }?.name ?: "Select Account",
                options = accounts.map { it.id to "${it.name} (${it.currency})" },
                onSelect = { selectedAccountId = it }
            )

            if (txType == TransactionType.TRANSFER) {
                val toCandidates = accounts.filter { it.id != selectedAccountId }
                SimpleDropdownField(
                    label = "To Account",
                    selectedLabel = toCandidates.firstOrNull { it.id == selectedToAccountId }?.name ?: "Select Destination",
                    options = toCandidates.map { it.id to "${it.name} (${it.currency})" },
                    onSelect = { selectedToAccountId = it }
                )
            } else {
                SimpleDropdownField(
                    label = "Category",
                    selectedLabel = filteredCategories.firstOrNull { it.first == selectedCategoryId }?.second?.trim()
                        ?: "Select Category",
                    options = filteredCategories,
                    onSelect = { selectedCategoryId = it }
                )
            }

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant / Payee") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(FormatUtils.formatDate(timestampMillis))
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(FormatUtils.formatTime(timestampMillis))
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    val accId = selectedAccountId
                    if (amount == null || amount <= 0.0) {
                        errorMessage = "Please enter a valid positive amount."
                        return@Button
                    }
                    if (accId == null) {
                        errorMessage = "Please select an account."
                        return@Button
                    }
                    if (txType == TransactionType.TRANSFER &&
                        (selectedToAccountId == null || selectedToAccountId == accId)
                    ) {
                        errorMessage = "Please choose a different destination account for transfer."
                        return@Button
                    }

                    val rateToBase = if (currency.equals(settings.baseCurrency, ignoreCase = true)) {
                        1.0
                    } else {
                        exchangeRateText.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0
                    }

                    val base = loadedTx
                    val toSave = TransactionEntity(
                        id = base?.id ?: 0L,
                        type = txType,
                        amount = amount,
                        currency = currency.uppercase(),
                        exchangeRateToBase = rateToBase,
                        baseAmount = LedgerCalculations.computeBaseAmount(amount, rateToBase),
                        accountId = accId,
                        toAccountId = if (txType == TransactionType.TRANSFER) selectedToAccountId else null,
                        categoryId = if (txType == TransactionType.TRANSFER) null else selectedCategoryId,
                        merchant = merchant.trim(),
                        note = note.trim(),
                        timestamp = timestampMillis,
                        source = base?.source ?: TransactionSource.MANUAL,
                        aiConfidence = base?.aiConfidence,
                        needsReview = false,
                        rawNotificationId = base?.rawNotificationId,
                        createdAt = base?.createdAt ?: System.currentTimeMillis()
                    )
                    viewModel.saveTransaction(toSave) {
                        onNavigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(if (loadedTx == null) "Save Transaction" else "Save Changes")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = timestampMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedUtcMillis = datePickerState.selectedDateMillis
                    if (selectedUtcMillis != null) {
                        val zone = ZoneId.systemDefault()
                        val newDate = Instant.ofEpochMilli(selectedUtcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                        val oldTime = Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalTime()
                        timestampMillis = ZonedDateTime.of(newDate, oldTime, zone).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val zone = ZoneId.systemDefault()
        val currentZoned = Instant.ofEpochMilli(timestampMillis).atZone(zone)
        val timeState = rememberTimePickerState(
            initialHour = currentZoned.hour,
            initialMinute = currentZoned.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val date = currentZoned.toLocalDate()
                    val newTime = LocalTime.of(timeState.hour, timeState.minute)
                    timestampMillis = ZonedDateTime.of(date, newTime, zone).toInstant().toEpochMilli()
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm && loadedTx != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transaction?") },
            text = { Text("This transaction will be removed from your balances and stats.") },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val id = loadedTx!!.id
                        showDeleteConfirm = false
                        viewModel.deleteTransaction(id)
                        onDeleted(id)
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyDropdown(
    selectedCurrency: String,
    onSelectCurrency: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedCurrency,
            onValueChange = {},
            readOnly = true,
            label = { Text("Currency") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FormatUtils.supportedCurrencies.forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = {
                        onSelectCurrency(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleDropdownField(
    label: String,
    selectedLabel: String,
    options: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    }
                )
            }
        }
    }
}
