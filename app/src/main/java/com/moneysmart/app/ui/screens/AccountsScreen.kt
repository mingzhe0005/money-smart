package com.moneysmart.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.ui.components.IconCatalog
import com.moneysmart.app.ui.theme.ExpenseRed
import com.moneysmart.app.ui.theme.IncomeGreen
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import com.moneysmart.app.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: MoneySmartViewModel,
    onQuickTransfer: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val accountsWithBalances by viewModel.accountsWithBalances.collectAsStateWithLifecycle()

    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var showAccountDialog by remember { mutableStateOf(false) }

    val included = accountsWithBalances.filter { it.account.includeInTotal }
    val assets = included.filter { it.currentBalance >= 0.0 }.sumOf { it.currentBalance }
    val liabilities = included.filter { it.currentBalance < 0.0 }.sumOf { -it.currentBalance }
    val netWorth = assets - liabilities

    val grouped = remember(accountsWithBalances) {
        accountsWithBalances.groupBy { it.account.type }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts & Wallets", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onQuickTransfer) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = "Transfer between accounts")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingAccount = null
                    showAccountDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add account")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Net Worth",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = FormatUtils.formatMoney(netWorth, settings.baseCurrency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Assets", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = FormatUtils.formatMoney(assets, settings.baseCurrency),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = IncomeGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Liabilities", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = FormatUtils.formatMoney(liabilities, settings.baseCurrency),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ExpenseRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            grouped.forEach { (type, list) ->
                item(key = "group_${type.name}") {
                    Text(
                        text = type.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                items(list, key = { it.account.id }) { item ->
                    val acc = item.account
                    val accent = Color(acc.colorHex)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingAccount = acc
                                showAccountDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = IconCatalog.resolve(acc.iconName),
                                    contentDescription = null,
                                    tint = accent
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = acc.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = buildString {
                                        append(acc.currency)
                                        if (!acc.includeInTotal) append(" · Excluded from Net Worth")
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = FormatUtils.formatMoney(item.currentBalance, acc.currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (item.currentBalance < 0) ExpenseRed else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAccountDialog) {
        AccountEditorDialog(
            initial = editingAccount,
            defaultCurrency = settings.baseCurrency,
            onDismiss = { showAccountDialog = false },
            onSave = { account ->
                viewModel.saveAccount(account)
                showAccountDialog = false
            },
            onArchive = { account ->
                viewModel.archiveAccount(account)
                showAccountDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountEditorDialog(
    initial: AccountEntity?,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSave: (AccountEntity) -> Unit,
    onArchive: (AccountEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: AccountType.BANK) }
    var currency by remember { mutableStateOf(initial?.currency ?: defaultCurrency) }
    var initialBalanceText by remember { mutableStateOf((initial?.initialBalance ?: 0.0).toString()) }
    var includeInTotal by remember { mutableStateOf(initial?.includeInTotal ?: true) }
    var typeExpanded by remember { mutableStateOf(false) }

    val defaultIcon = when (type) {
        AccountType.CASH -> "AccountBalanceWallet"
        AccountType.BANK -> "AccountBalance"
        AccountType.CREDIT_CARD -> "CreditCard"
        AccountType.E_WALLET -> "Smartphone"
        AccountType.INVESTMENT -> "TrendingUp"
        AccountType.OTHER -> "AccountBalanceWallet"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Account" else "Edit Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name (e.g., DBS Multiplier, GrabPay)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        value = type.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        AccountType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.displayName) },
                                onClick = {
                                    type = t
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = initialBalanceText,
                        onValueChange = { initialBalanceText = it },
                        label = { Text("Initial Balance") },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Include in Net Worth", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = includeInTotal, onCheckedChange = { includeInTotal = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val bal = initialBalanceText.toDoubleOrNull() ?: 0.0
                    onSave(
                        AccountEntity(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            type = type,
                            currency = currency.uppercase(),
                            initialBalance = bal,
                            iconName = initial?.iconName ?: defaultIcon,
                            colorHex = initial?.colorHex ?: 0xFF0D6E52L,
                            includeInTotal = includeInTotal,
                            isArchived = false,
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
                        onClick = { onArchive(initial) }
                    ) { Text("Archive") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
