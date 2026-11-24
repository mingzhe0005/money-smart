package com.moneysmart.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneysmart.app.data.local.CategoryOrganizer
import com.moneysmart.app.data.local.entity.CategoryType
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionSource
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.data.repository.LedgerCalculations
import com.moneysmart.app.ui.components.EspressoPillButton
import com.moneysmart.app.ui.components.ResponsiveContentBox
import com.moneysmart.app.ui.components.SoftElevatedCard
import com.moneysmart.app.ui.components.WarmCurvedHeroHeader
import com.moneysmart.app.ui.components.WarmSegmentedPillBar
import com.moneysmart.app.ui.components.rememberIsWideScreen
import com.moneysmart.app.ui.theme.CoralAccent
import com.moneysmart.app.ui.theme.CoralSoft
import com.moneysmart.app.ui.theme.EspressoInk
import com.moneysmart.app.ui.theme.PeachSoft
import com.moneysmart.app.ui.theme.PeachTint
import com.moneysmart.app.ui.theme.WarningAmber
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import com.moneysmart.app.util.FormatUtils
import kotlinx.coroutines.launch
import java.time.Instant
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
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
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
    var showSplitDialog by remember { mutableStateOf(false) }
    var showLinkRepaymentsDialog by remember { mutableStateOf(false) }

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
                amountText = FormatUtils.formatCompactNumber(existing.amount)
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

    val isWide = rememberIsWideScreen()

    fun attemptSave() {
        val amount = amountText.toDoubleOrNull()
        val accId = selectedAccountId
        if (amount == null || amount <= 0.0) {
            errorMessage = "Please enter a valid positive amount."
            return
        }
        if (accId == null) {
            errorMessage = "Please select an account."
            return
        }
        if (txType == TransactionType.TRANSFER &&
            (selectedToAccountId == null || selectedToAccountId == accId)
        ) {
            errorMessage = "Please choose a different destination account for transfer."
            return
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
            createdAt = base?.createdAt ?: System.currentTimeMillis(),
            reimbursedAmount = base?.reimbursedAmount ?: 0.0,
            reimbursedBaseAmount = base?.reimbursedBaseAmount ?: 0.0,
            isReimbursement = txType == TransactionType.INCOME &&
                (base?.splitBillId != null ||
                    CategoryOrganizer.isReimbursementCategoryName(
                        categories.firstOrNull { it.id == selectedCategoryId }?.name
                    )),
            splitBillId = base?.splitBillId
        )
        viewModel.saveTransaction(toSave) {
            onNavigateBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        WarmCurvedHeroHeader(
            title = if (loadedTx == null) "New Transaction" else "Edit Transaction",
            subtitle = if (loadedTx == null) "Log expense, income, or transfer" else "Update amount, account, or bill split",
            heroColor = PeachSoft,
            maxWidth = if (isWide) 980.dp else 720.dp,
            onNavigateBack = onNavigateBack,
            actions = {
                if (loadedTx != null) {
                    Surface(
                        onClick = { showDeleteConfirm = true },
                        shape = RoundedCornerShape(12.dp),
                        color = CoralSoft
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete",
                                tint = CoralAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            if (isWide) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", style = MaterialTheme.typography.labelLarge, color = CoralAccent)
                            }
                        }
                    }
                }
                EspressoPillButton(
                    text = "Save",
                    icon = Icons.Rounded.Check,
                    onClick = { attemptSave() },
                    modifier = Modifier.height(40.dp)
                )
            }
        )

        ResponsiveContentBox(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            maxWidth = if (isWide) 980.dp else 720.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (loadedTx?.source == TransactionSource.AI_NOTIFICATION) {
                    Surface(
                        color = if (needsReview) WarningAmber.copy(alpha = 0.14f) else PeachTint,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = CoralAccent)
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

                @Composable
                fun PrimaryFormColumn(modifier: Modifier = Modifier) {
                    SoftElevatedCard(modifier = modifier, cornerRadius = 22.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            WarmSegmentedPillBar(
                                options = TransactionType.entries.map { it.displayName },
                                selectedIndex = txType.ordinal,
                                onSelect = { txType = TransactionType.entries[it] }
                            )

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
                                    shape = RoundedCornerShape(14.dp),
                                    trailingIcon = clearFieldIcon(
                                        visible = amountText.isNotEmpty(),
                                        contentDescription = "Clear amount",
                                        onClear = { amountText = "" }
                                    ),
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
                                        shape = RoundedCornerShape(14.dp),
                                        trailingIcon = clearFieldIcon(
                                            visible = exchangeRateText.isNotEmpty(),
                                            contentDescription = "Clear exchange rate",
                                            onClear = { exchangeRateText = "" }
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "≈ ${FormatUtils.formatMoney(basePreview, settings.baseCurrency)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
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
                        }
                    }
                }

                @Composable
                fun SecondaryFormColumn(modifier: Modifier = Modifier) {
                    SoftElevatedCard(modifier = modifier, cornerRadius = 22.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = merchant,
                                onValueChange = { merchant = it },
                                label = {
                                    Text(
                                        if (txType == TransactionType.TRANSFER) {
                                            "Transfer Reference / Description (Optional)"
                                        } else {
                                            "Merchant / Payee"
                                        }
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                trailingIcon = clearFieldIcon(
                                    visible = merchant.isNotEmpty(),
                                    contentDescription = "Clear merchant",
                                    onClear = { merchant = "" }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text("Note") },
                                maxLines = 3,
                                shape = RoundedCornerShape(14.dp),
                                trailingIcon = clearFieldIcon(
                                    visible = note.isNotEmpty(),
                                    contentDescription = "Clear note",
                                    onClear = { note = "" }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showDatePicker = true },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(FormatUtils.formatDate(timestampMillis))
                                }
                                OutlinedButton(
                                    onClick = { showTimePicker = true },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
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

                            EspressoPillButton(
                                text = if (loadedTx == null) "Save Transaction" else "Save Changes",
                                onClick = { attemptSave() },
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (loadedTx != null && loadedTx?.type == TransactionType.EXPENSE) {
                                OutlinedButton(
                                    onClick = { showLinkRepaymentsDialog = true },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, CoralAccent.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Rounded.Payments, contentDescription = null, tint = CoralAccent)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Link Friend Repayments",
                                        color = EspressoInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (loadedTx?.splitBillId == null) {
                                    OutlinedButton(
                                        onClick = { showSplitDialog = true },
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, CoralAccent.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Rounded.Groups, contentDescription = null, tint = CoralAccent)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Split This Bill with Friends",
                                            color = EspressoInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        PrimaryFormColumn(modifier = Modifier.weight(1f))
                        SecondaryFormColumn(modifier = Modifier.weight(1f))
                    }
                } else {
                    PrimaryFormColumn(modifier = Modifier.fillMaxWidth())
                    SecondaryFormColumn(modifier = Modifier.fillMaxWidth())
                }
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

    if (showSplitDialog && loadedTx != null) {
        val baseTx = loadedTx!!
        CreateSplitBillDialog(
            initialTitle = baseTx.note.ifBlank { baseTx.merchant.ifBlank { "Shared Expense" } },
            initialAmount = baseTx.amount,
            initialCurrency = baseTx.currency,
            initialAccountId = baseTx.accountId,
            initialCategoryId = baseTx.categoryId,
            lockExpenseDetails = true,
            accounts = accounts,
            categories = categories,
            onDismiss = { showSplitDialog = false },
            onSave = { title, total, cur, accId, catId, participants ->
                viewModel.createSplitBill(
                    title = title,
                    totalAmount = total,
                    currency = cur,
                    paidAccountId = accId,
                    categoryId = catId,
                    existingExpenseTxId = baseTx.id,
                    participants = participants,
                    onCreated = {
                        showSplitDialog = false
                        onNavigateBack()
                    }
                )
            }
        )
    }

    if (showLinkRepaymentsDialog && loadedTx != null) {
        val baseTx = loadedTx!!
        val accountMap = remember(accounts) { accounts.associateBy { it.id } }
        val recentIncomeCandidates = remember(allTransactions) {
            allTransactions.filter {
                !it.isDeleted && it.type == TransactionType.INCOME && !it.isReimbursement
            }.sortedByDescending { it.timestamp }.take(40)
        }
        var selectedIncomeIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
        var searchIncome by remember { mutableStateOf("") }

        val visibleCandidates = remember(recentIncomeCandidates, searchIncome, accountMap) {
            val q = searchIncome.trim()
            if (q.isEmpty()) recentIncomeCandidates
            else recentIncomeCandidates.filter { tx ->
                tx.merchant.contains(q, true) ||
                    tx.note.contains(q, true) ||
                    (accountMap[tx.accountId]?.name?.contains(q, true) == true) ||
                    tx.amount.toString().contains(q)
            }
        }

        val selectedSum = recentIncomeCandidates
            .filter { it.id in selectedIncomeIds }
            .sumOf { it.amount }
        val newNetExpense = (baseTx.amount - baseTx.reimbursedAmount - selectedSum).coerceAtLeast(0.0)

        AlertDialog(
            onDismissRequest = { showLinkRepaymentsDialog = false },
            title = { Text("Link Friend Repayments") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pick recent incoming transfers from friends for this expense. They stay in their destination accounts and reduce this expense instead of counting as income.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = searchIncome,
                        onValueChange = { searchIncome = it },
                        placeholder = { Text("Search friend, account, or amount...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "New Net Expense: ${FormatUtils.formatMoney(newNetExpense, baseTx.currency)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = CoralAccent
                    )
                    if (visibleCandidates.isEmpty()) {
                        Text(
                            text = "No unlinked recent income transactions found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp, max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(visibleCandidates, key = { it.id }) { incTx ->
                                val checked = incTx.id in selectedIncomeIds
                                val accName = accountMap[incTx.accountId]?.name ?: "Account"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedIncomeIds = if (checked) {
                                                selectedIncomeIds - incTx.id
                                            } else {
                                                selectedIncomeIds + incTx.id
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            selectedIncomeIds = if (isChecked) {
                                                selectedIncomeIds + incTx.id
                                            } else {
                                                selectedIncomeIds - incTx.id
                                            }
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = incTx.note.ifBlank { incTx.merchant.ifBlank { "Income Transfer" } },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "$accName · ${FormatUtils.formatDate(incTx.timestamp)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = FormatUtils.formatMoney(incTx.amount, incTx.currency),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedIncomeIds.isNotEmpty(),
                    onClick = {
                        val ids = selectedIncomeIds.toList()
                        showLinkRepaymentsDialog = false
                        viewModel.linkFriendRepaymentsToExpense(baseTx.id, ids) {
                            onNavigateBack()
                        }
                    }
                ) { Text("Link Selected (${selectedIncomeIds.size})") }
            },
            dismissButton = {
                TextButton(onClick = { showLinkRepaymentsDialog = false }) { Text("Cancel") }
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
            shape = RoundedCornerShape(14.dp),
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
            shape = RoundedCornerShape(14.dp),
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

private fun clearFieldIcon(
    visible: Boolean,
    contentDescription: String,
    onClear: () -> Unit
): (@Composable () -> Unit)? = if (!visible) {
    null
} else {
    {
        IconButton(onClick = onClear) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
