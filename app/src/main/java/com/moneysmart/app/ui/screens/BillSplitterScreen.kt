package com.moneysmart.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.CategoryType
import com.moneysmart.app.data.local.entity.SplitParticipantEntity
import com.moneysmart.app.data.repository.LedgerCalculations
import com.moneysmart.app.data.repository.SplitBillWithParticipants
import com.moneysmart.app.ui.components.EmptyStateCard
import com.moneysmart.app.ui.components.ResponsiveContentBox
import com.moneysmart.app.ui.components.TopBarAddButton
import com.moneysmart.app.ui.components.rememberIsWideScreen
import com.moneysmart.app.ui.theme.IncomeGreen
import com.moneysmart.app.ui.theme.WarningAmber
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import com.moneysmart.app.util.FormatUtils

private enum class SplitFilter(val label: String) {
    PENDING("Pending"),
    SETTLED("Settled"),
    ALL("All")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillSplitterScreen(
    viewModel: MoneySmartViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val splitBills by viewModel.splitBills.collectAsStateWithLifecycle()
    val accounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    var filter by remember { mutableStateOf(SplitFilter.PENDING) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var settleTarget by remember { mutableStateOf<Pair<SplitBillWithParticipants, SplitParticipantEntity>?>(null) }

    val totalPendingOwed = remember(splitBills) {
        splitBills.sumOf { it.remainingOwed }
    }
    val pendingBillCount = remember(splitBills) {
        splitBills.count { !it.isFullySettled }
    }

    val filteredBills = remember(splitBills, filter) {
        when (filter) {
            SplitFilter.PENDING -> splitBills.filter { !it.isFullySettled }
            SplitFilter.SETTLED -> splitBills.filter { it.isFullySettled }
            SplitFilter.ALL -> splitBills
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bill Splitter & Reimbursements", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TopBarAddButton(
                        label = "New Split",
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        ResponsiveContentBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            maxWidth = 920.dp
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Total Owed to You",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = FormatUtils.formatMoney(totalPendingOwed, settings.baseCurrency),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$pendingBillCount active split bill(s) · Repayments go directly to your chosen account without inflating income",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SplitFilter.entries.forEachIndexed { idx, f ->
                            SegmentedButton(
                                selected = filter == f,
                                onClick = { filter = f },
                                shape = SegmentedButtonDefaults.itemShape(idx, SplitFilter.entries.size)
                            ) {
                                Text(f.label, fontSize = 13.sp)
                            }
                        }
                    }
                }

                if (filteredBills.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No ${filter.label.lowercase()} split bills",
                            subtitle = "Create a split bill for group dinners, or open any expense transaction and tap 'Split This Bill with Friends'.",
                            actionLabel = "Create Split Bill",
                            onAction = { showCreateDialog = true }
                        )
                    }
                } else {
                    items(filteredBills, key = { it.bill.id }) { item ->
                        val bill = item.bill
                        val paidAccountName = accountMap[bill.paidAccountId]?.name ?: "Account"
                        val catName = bill.categoryId?.let { categoryMap[it]?.name } ?: "Shared Expense"
                        val progress = if (item.totalOwedByFriends > 0.0) {
                            (item.totalRepaid / item.totalOwedByFriends).toFloat().coerceIn(0f, 1f)
                        } else 1f

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bill.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${FormatUtils.formatDate(bill.timestamp)} · Paid via $paidAccountName · $catName",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteSplitBill(bill.id) }) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Delete split record",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Total Paid: ${FormatUtils.formatMoney(bill.totalAmount, bill.currency)}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Your Share: ${FormatUtils.formatMoney(bill.myShareAmount, bill.currency)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { progress },
                                    color = if (item.isFullySettled) IncomeGreen else WarningAmber,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                                Text(
                                    text = "Repaid: ${FormatUtils.formatMoney(item.totalRepaid, bill.currency)} / ${FormatUtils.formatMoney(item.totalOwedByFriends, bill.currency)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                HorizontalDivider()

                                item.participants.forEach { p ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = p.friendName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (p.isSettled) {
                                                val destName = p.repaidAccountId?.let { accountMap[it]?.name } ?: "Account"
                                                Text(
                                                    text = "Paid ${FormatUtils.formatMoney(p.paidAmount, bill.currency)} → $destName",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = IncomeGreen
                                                )
                                            } else {
                                                Text(
                                                    text = "Owes ${FormatUtils.formatMoney(p.owedAmount, bill.currency)}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = WarningAmber
                                                )
                                            }
                                        }

                                        if (p.isSettled) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Rounded.CheckCircle,
                                                    contentDescription = "Settled",
                                                    tint = IncomeGreen
                                                )
                                                TextButton(onClick = { viewModel.unsettleSplitParticipant(p.id) }) {
                                                    Text("Undo")
                                                }
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = { settleTarget = item to p }
                                            ) {
                                                Text("Mark Paid")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog && accounts.isNotEmpty()) {
        CreateSplitBillDialog(
            initialTitle = "",
            initialAmount = null,
            initialCurrency = settings.baseCurrency,
            initialAccountId = accounts.first().id,
            initialCategoryId = categories.firstOrNull { it.type == CategoryType.EXPENSE }?.id,
            lockExpenseDetails = false,
            accounts = accounts,
            categories = categories,
            onDismiss = { showCreateDialog = false },
            onSave = { title, total, currency, accId, catId, participants ->
                viewModel.createSplitBill(
                    title = title,
                    totalAmount = total,
                    currency = currency,
                    paidAccountId = accId,
                    categoryId = catId,
                    existingExpenseTxId = null,
                    participants = participants
                )
                showCreateDialog = false
            }
        )
    }

    if (settleTarget != null && accounts.isNotEmpty()) {
        val (billItem, participant) = settleTarget!!
        SettleParticipantDialog(
            friendName = participant.friendName,
            owedAmount = participant.owedAmount,
            currency = billItem.bill.currency,
            defaultAccountId = billItem.bill.paidAccountId,
            accounts = accounts,
            onDismiss = { settleTarget = null },
            onConfirm = { destAccountId, paidAmt ->
                viewModel.settleSplitParticipant(participant.id, destAccountId, paidAmt)
                settleTarget = null
            }
        )
    }
}

@Composable
private fun SettleParticipantDialog(
    friendName: String,
    owedAmount: Double,
    currency: String,
    defaultAccountId: Long,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Long, Double) -> Unit
) {
    var selectedAccountId by remember { mutableStateOf(defaultAccountId) }
    var amountText by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", owedAmount)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Repayment from $friendName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Select which account $friendName transferred the money into:",
                    style = MaterialTheme.typography.bodyMedium
                )
                SimpleDropdownField(
                    label = "Received Into Account",
                    selectedLabel = accounts.firstOrNull { it.id == selectedAccountId }?.name ?: "Select Account",
                    options = accounts.map { it.id to "${it.name} (${it.currency})" },
                    onSelect = { selectedAccountId = it }
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount Repaid ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val amt = amountText.toDoubleOrNull()
            TextButton(
                enabled = amt != null && amt > 0.0,
                onClick = { onConfirm(selectedAccountId, amt!!) }
            ) { Text("Mark Paid") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CreateSplitBillDialog(
    initialTitle: String,
    initialAmount: Double?,
    initialCurrency: String,
    initialAccountId: Long,
    initialCategoryId: Long?,
    lockExpenseDetails: Boolean,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        totalAmount: Double,
        currency: String,
        paidAccountId: Long,
        categoryId: Long?,
        participants: List<Pair<String, Double>>
    ) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var totalText by remember {
        mutableStateOf(initialAmount?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "")
    }
    var currency by remember { mutableStateOf(initialCurrency) }
    var paidAccountId by remember { mutableStateOf(initialAccountId) }
    val expenseCategories = remember(categories) { categories.filter { it.type == CategoryType.EXPENSE } }
    var categoryId by remember { mutableStateOf(initialCategoryId ?: expenseCategories.firstOrNull()?.id) }
    var equalSplit by remember { mutableStateOf(true) }

    val friendNames = remember { mutableStateListOf("Friend 1") }
    val friendAmounts = remember { mutableStateListOf("") }

    val totalAmount = totalText.toDoubleOrNull() ?: 0.0

    LaunchedEffect(equalSplit, totalAmount, friendNames.size) {
        if (equalSplit && totalAmount > 0.0 && friendNames.isNotEmpty()) {
            val (_, shares) = LedgerCalculations.computeEqualSplitShares(totalAmount, friendNames.size)
            for (i in shares.indices) {
                if (i < friendAmounts.size) {
                    friendAmounts[i] = String.format(java.util.Locale.US, "%.2f", shares[i])
                }
            }
        }
    }

    val friendsTotal = friendAmounts.sumOf { it.toDoubleOrNull() ?: 0.0 }
    val mySharePreview = (totalAmount - friendsTotal).coerceAtLeast(0.0)

    val isWide = rememberIsWideScreen()
    val validParticipants = friendNames.indices.mapNotNull { i ->
        val name = friendNames[i].trim()
        val owed = friendAmounts[i].toDoubleOrNull() ?: 0.0
        if (name.isNotBlank() && owed > 0.0) name to owed else null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = if (isWide) 700.dp else 520.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (lockExpenseDetails) "Split This Bill" else "New Split Bill",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 10.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!lockExpenseDetails) {
                        if (isWide) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = title,
                                    onValueChange = { title = it },
                                    label = { Text("Description (e.g. Dinner)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.4f)
                                )
                                OutlinedTextField(
                                    value = totalText,
                                    onValueChange = { totalText = it },
                                    label = { Text("Total Paid") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                CurrencyDropdown(
                                    selectedCurrency = currency,
                                    onSelectCurrency = { currency = it },
                                    modifier = Modifier.weight(0.8f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SimpleDropdownField(
                                    label = "Paid Using Account",
                                    selectedLabel = accounts.firstOrNull { it.id == paidAccountId }?.name ?: "Select",
                                    options = accounts.map { it.id to "${it.name} (${it.currency})" },
                                    onSelect = { paidAccountId = it },
                                    modifier = Modifier.weight(1f)
                                )
                                SimpleDropdownField(
                                    label = "Category",
                                    selectedLabel = expenseCategories.firstOrNull { it.id == categoryId }?.name ?: "Select",
                                    options = expenseCategories.map { it.id to it.name },
                                    onSelect = { categoryId = it },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Description (e.g. Dinner at Haidilao)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = totalText,
                                    onValueChange = { totalText = it },
                                    label = { Text("Total Paid") },
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
                                label = "Paid Using Account",
                                selectedLabel = accounts.firstOrNull { it.id == paidAccountId }?.name ?: "Select",
                                options = accounts.map { it.id to "${it.name} (${it.currency})" },
                                onSelect = { paidAccountId = it }
                            )
                            SimpleDropdownField(
                                label = "Category",
                                selectedLabel = expenseCategories.firstOrNull { it.id == categoryId }?.name ?: "Select",
                                options = expenseCategories.map { it.id to it.name },
                                onSelect = { categoryId = it }
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Description") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Bill Total: ${FormatUtils.formatMoney(totalAmount, currency)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(true to "Equal Split", false to "Custom Amounts").forEachIndexed { idx, (isEq, lbl) ->
                            SegmentedButton(
                                selected = equalSplit == isEq,
                                onClick = { equalSplit = isEq },
                                shape = SegmentedButtonDefaults.itemShape(idx, 2)
                            ) {
                                Text(lbl, fontSize = 13.sp)
                            }
                        }
                    }

                    Text(
                        text = "Friends (${friendNames.size}) · Your Share: ${FormatUtils.formatMoney(mySharePreview, currency)}" +
                            if (equalSplit) " (auto-calculated)" else "",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    for (i in friendNames.indices) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = friendNames[i],
                                onValueChange = { friendNames[i] = it },
                                label = { Text("Friend Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1.3f)
                            )
                            OutlinedTextField(
                                value = friendAmounts[i],
                                onValueChange = {
                                    if (!equalSplit) {
                                        friendAmounts[i] = it
                                    }
                                },
                                readOnly = equalSplit,
                                enabled = !equalSplit || friendAmounts[i].isNotBlank(),
                                label = { Text(if (equalSplit) "Owes (Auto)" else "Owes") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = if (equalSplit) {
                                    OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                } else {
                                    OutlinedTextFieldDefaults.colors()
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (friendNames.size > 1) {
                                IconButton(onClick = {
                                    friendNames.removeAt(i)
                                    friendAmounts.removeAt(i)
                                }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove friend")
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            friendNames.add("Friend ${friendNames.size + 1}")
                            friendAmounts.add("")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Another Friend")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        enabled = totalAmount > 0.0 && validParticipants.isNotEmpty(),
                        onClick = {
                            onSave(
                                title.trim().ifBlank { "Shared Bill" },
                                totalAmount,
                                currency,
                                paidAccountId,
                                categoryId,
                                validParticipants
                            )
                        }
                    ) {
                        Text("Create Split")
                    }
                }
            }
        }
    }
}
