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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.moneysmart.app.data.repository.AccountWithBalance
import com.moneysmart.app.ui.components.IconCatalog
import com.moneysmart.app.ui.theme.ExpenseRed
import com.moneysmart.app.ui.theme.IncomeGreen
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import com.moneysmart.app.util.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: MoneySmartViewModel,
    onQuickTransfer: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val accountsWithBalances by viewModel.accountsWithBalances.collectAsStateWithLifecycle()

    var editingItem by remember { mutableStateOf<AccountWithBalance?>(null) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showCalibrateConfirm by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var isSyncing by remember { mutableStateOf(false) }

    val included = accountsWithBalances.filter { it.account.includeInTotal }
    val assets = included.filter { it.balanceInBaseCurrency >= 0.0 }.sumOf { it.balanceInBaseCurrency }
    val liabilities = included.filter { it.balanceInBaseCurrency < 0.0 }.sumOf { -it.balanceInBaseCurrency }
    val netWorth = assets - liabilities

    val accountNameMap = remember(accountsWithBalances) {
        accountsWithBalances.associate { it.account.id to it.account.name }
    }
    val grouped = remember(accountsWithBalances) {
        accountsWithBalances.groupBy { it.account.type }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts & Wallets", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        enabled = !isSyncing,
                        onClick = {
                            isSyncing = true
                            statusBanner = "Syncing live investments & rates..."
                            scope.launch {
                                val res = viewModel.syncLiveInvestmentsNow()
                                statusBanner = res.fold(
                                    onSuccess = { count ->
                                        if (count > 0) {
                                            "Synced $count live investment account(s)."
                                        } else {
                                            "To auto-sync an investment (like Mari Gold), tap it and enter its ticker (e.g. XAU_GRAM) and units/grams."
                                        }
                                    },
                                    onFailure = { err -> "Live sync failed: ${err.message}" }
                                )
                                isSyncing = false
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Sync live investment prices")
                    }
                    IconButton(onClick = { showCalibrateConfirm = true }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Apply screenshot balances")
                    }
                    IconButton(onClick = onQuickTransfer) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = "Transfer between accounts")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingItem = null
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
            if (statusBanner != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = statusBanner!!,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { statusBanner = null }) { Text("OK") }
                        }
                    }
                }
            }

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
                            text = "Net Worth (${settings.baseCurrency})",
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
                    val subtitle = buildString {
                        append(acc.currency)
                        if (acc.autoRechargeSourceAccountId != null) {
                            val srcName = accountNameMap[acc.autoRechargeSourceAccountId] ?: "Linked Bank"
                            append(" · Auto-recharge from $srcName")
                        }
                        if (item.isLiveMarketValue && !acc.marketSymbol.isNullOrBlank()) {
                            append(" · Live (${acc.marketSymbol} × ${acc.holdingUnits})")
                        } else if (acc.type == AccountType.INVESTMENT && !acc.marketSymbol.isNullOrBlank()) {
                            append(" · Set units to auto-sync (${acc.marketSymbol})")
                        }
                        if (!acc.includeInTotal) append(" · Excluded from Net Worth")
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingItem = item
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
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = FormatUtils.formatMoney(item.currentBalance, acc.currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.currentBalance < 0) ExpenseRed else MaterialTheme.colorScheme.onSurface
                                )
                                if (!acc.currency.equals(settings.baseCurrency, ignoreCase = true)) {
                                    Text(
                                        text = "≈ ${FormatUtils.formatMoney(item.balanceInBaseCurrency, settings.baseCurrency)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAccountDialog) {
        AccountEditorDialog(
            initialItem = editingItem,
            allAccounts = accountsWithBalances.map { it.account },
            defaultCurrency = settings.baseCurrency,
            onDismiss = { showAccountDialog = false },
            onSave = { account, targetCurrentBalance ->
                viewModel.saveAccountWithTargetBalance(account, targetCurrentBalance)
                showAccountDialog = false
            },
            onArchive = { account ->
                viewModel.archiveAccount(account)
                showAccountDialog = false
            }
        )
    }

    if (showCalibrateConfirm) {
        AlertDialog(
            onDismissRequest = { showCalibrateConfirm = false },
            title = { Text("Calibrate to Money Manager Balances?") },
            text = {
                Text(
                    "Sets all 18 accounts (Cash, OCBC, POSB, PayLah -10.90, Investments, EUR wallets) to match your Money Manager screenshot balances and links PayLah auto-recharge to POSB, without deleting any imported transactions."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCalibrateConfirm = false
                    scope.launch {
                        val count = viewModel.applyScreenshotBalancesNow()
                        statusBanner = "Calibrated $count accounts to your Money Manager balances and linked PayLah → POSB auto-recharge."
                    }
                }) { Text("Apply Balances") }
            },
            dismissButton = {
                TextButton(onClick = { showCalibrateConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountEditorDialog(
    initialItem: AccountWithBalance?,
    allAccounts: List<AccountEntity>,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSave: (AccountEntity, Double) -> Unit,
    onArchive: (AccountEntity) -> Unit
) {
    val initial = initialItem?.account
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: AccountType.BANK) }
    var currency by remember { mutableStateOf(initial?.currency ?: defaultCurrency) }
    var balanceText by remember {
        mutableStateOf(
            String.format(java.util.Locale.US, "%.2f", initialItem?.currentBalance ?: 0.0)
        )
    }
    var includeInTotal by remember { mutableStateOf(initial?.includeInTotal ?: true) }
    var rechargeSourceId by remember { mutableStateOf(initial?.autoRechargeSourceAccountId) }
    var marketSymbol by remember { mutableStateOf(initial?.marketSymbol ?: "") }
    var holdingUnitsText by remember { mutableStateOf(initial?.holdingUnits?.toString() ?: "") }

    var typeExpanded by remember { mutableStateOf(false) }
    var rechargeExpanded by remember { mutableStateOf(false) }

    val defaultIcon = when (type) {
        AccountType.CASH -> "AccountBalanceWallet"
        AccountType.BANK -> "AccountBalance"
        AccountType.CREDIT_CARD -> "CreditCard"
        AccountType.E_WALLET -> "Smartphone"
        AccountType.INVESTMENT -> "TrendingUp"
        AccountType.OTHER -> "AccountBalanceWallet"
    }

    val rechargeCandidates = remember(allAccounts, initial) {
        allAccounts.filter { it.id != initial?.id }
    }
    val rechargeSourceLabel = rechargeSourceId?.let { id ->
        rechargeCandidates.firstOrNull { it.id == id }?.name
    } ?: "None (Disabled)"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Account" else "Edit Account") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name (e.g., OCBC, PayLah)") },
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
                        value = balanceText,
                        onValueChange = { balanceText = it },
                        label = { Text(if (initial == null) "Initial Balance" else "Current Balance") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                    CurrencyDropdown(
                        selectedCurrency = currency,
                        onSelectCurrency = { currency = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (type == AccountType.E_WALLET || type == AccountType.BANK || type == AccountType.OTHER) {
                    ExposedDropdownMenuBox(
                        expanded = rechargeExpanded,
                        onExpandedChange = { rechargeExpanded = !rechargeExpanded }
                    ) {
                        OutlinedTextField(
                            value = rechargeSourceLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Auto-Recharge From (if < 0)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(rechargeExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = rechargeExpanded,
                            onDismissRequest = { rechargeExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None (Disabled)") },
                                onClick = {
                                    rechargeSourceId = null
                                    rechargeExpanded = false
                                }
                            )
                            rechargeCandidates.forEach { src ->
                                DropdownMenuItem(
                                    text = { Text("${src.name} (${src.currency})") },
                                    onClick = {
                                        rechargeSourceId = src.id
                                        rechargeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (type == AccountType.INVESTMENT) {
                    OutlinedTextField(
                        value = marketSymbol,
                        onValueChange = { marketSymbol = it },
                        label = { Text("Live Ticker (e.g. XAU_GRAM, ES3.SI, VWRA.L, SPY)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = holdingUnitsText,
                        onValueChange = { holdingUnitsText = it },
                        label = { Text("Units / Shares / Gold Grams (for live sync)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
                    val targetBal = balanceText.toDoubleOrNull() ?: 0.0
                    val parsedUnits = holdingUnitsText.toDoubleOrNull()?.takeIf { it > 0.0 }
                    val cleanSymbol = marketSymbol.trim().uppercase().takeIf { it.isNotEmpty() }
                    onSave(
                        AccountEntity(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            type = type,
                            currency = currency.uppercase(),
                            initialBalance = initial?.initialBalance ?: targetBal,
                            iconName = initial?.iconName ?: defaultIcon,
                            colorHex = initial?.colorHex ?: 0xFF0D6E52L,
                            includeInTotal = includeInTotal,
                            isArchived = false,
                            createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                            autoRechargeSourceAccountId = rechargeSourceId,
                            autoRechargeMinBalance = initial?.autoRechargeMinBalance ?: 0.0,
                            marketSymbol = cleanSymbol,
                            holdingUnits = parsedUnits,
                            lastSyncedMarketValue = if (parsedUnits != null && cleanSymbol != null) {
                                initial?.lastSyncedMarketValue
                            } else {
                                null
                            },
                            lastMarketSyncAt = initial?.lastMarketSyncAt ?: 0L
                        ),
                        targetBal
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
