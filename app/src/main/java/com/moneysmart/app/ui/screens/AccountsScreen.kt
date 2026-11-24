package com.moneysmart.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.data.repository.AccountWithBalance
import com.moneysmart.app.ui.components.BauhausCornerArt
import com.moneysmart.app.ui.components.EspressoPillButton
import com.moneysmart.app.ui.components.IconCatalog
import com.moneysmart.app.ui.components.LabeledActionButton
import com.moneysmart.app.ui.components.ResponsiveContentBox
import com.moneysmart.app.ui.components.SoftElevatedCard
import com.moneysmart.app.ui.components.SquircleIconBadge
import com.moneysmart.app.ui.components.TopBarAddButton
import com.moneysmart.app.ui.components.WarmCurvedHeroHeader
import com.moneysmart.app.ui.components.rememberIsWideScreen
import com.moneysmart.app.ui.theme.ApricotHero
import com.moneysmart.app.ui.theme.CoralAccent
import com.moneysmart.app.ui.theme.CoralSoft
import com.moneysmart.app.ui.theme.EspressoInk
import com.moneysmart.app.ui.theme.ExpenseRed
import com.moneysmart.app.ui.theme.IncomeGreenDark
import com.moneysmart.app.ui.theme.PeachTint
import com.moneysmart.app.ui.theme.TrackBeige
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import com.moneysmart.app.util.FormatUtils
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(
    viewModel: MoneySmartViewModel,
    onQuickTransfer: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val accountsWithBalances by viewModel.accountsWithBalances.collectAsStateWithLifecycle()
    val isWide = rememberIsWideScreen()
    val isDark = isSystemInDarkTheme()

    var editingItem by remember { mutableStateOf<AccountWithBalance?>(null) }
    var showAccountDialog by remember { mutableStateOf(false) }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WarmCurvedHeroHeader(
            title = "Accounts",
            subtitle = "${accountsWithBalances.size} active balances · Auto-recharge & live",
            heroColor = ApricotHero,
            useLightArtOnApricot = true,
            maxWidth = if (isWide) 1040.dp else 920.dp,
            actions = {
                LabeledActionButton(
                    icon = Icons.Rounded.Refresh,
                    label = if (isSyncing) "Syncing..." else "Sync Prices",
                    enabled = !isSyncing,
                    onClick = {
                        isSyncing = true
                        statusBanner = "Syncing live investments & exchange rates..."
                        scope.launch {
                            val res = viewModel.syncLiveInvestmentsNow()
                            statusBanner = res.fold(
                                onSuccess = { count ->
                                    if (count > 0) {
                                        "Synced $count live investment account(s)."
                                    } else {
                                        "All accounts up to date. Tap an investment account to set a live price ticker and units."
                                    }
                                },
                                onFailure = { err -> "Live sync failed: ${err.message}" }
                            )
                            isSyncing = false
                        }
                    }
                )
                LabeledActionButton(
                    icon = Icons.Rounded.SwapHoriz,
                    label = "Transfer",
                    onClick = onQuickTransfer
                )
                TopBarAddButton(
                    label = "Add",
                    onClick = {
                        editingItem = null
                        showAccountDialog = true
                    }
                )
            }
        )

        ResponsiveContentBox(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            maxWidth = if (isWide) 1040.dp else 920.dp
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (statusBanner != null) {
                    item {
                        Surface(
                            color = PeachTint,
                            border = BorderStroke(1.dp, CoralAccent.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(16.dp),
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
                                    color = EspressoInk,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { statusBanner = null }) {
                                    Text("OK", color = CoralAccent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Signature Dark Espresso Net Worth Hero Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else EspressoInk,
                        shadowElevation = 4.dp
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            BauhausCornerArt(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(width = 160.dp, height = 100.dp),
                                darkAccent = ApricotHero.copy(alpha = 0.16f),
                                coralAccent = CoralAccent.copy(alpha = 0.85f),
                                apricotAccent = ApricotHero.copy(alpha = 0.7f)
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Net Worth (${settings.baseCurrency})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White.copy(alpha = 0.74f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = FormatUtils.formatMoney(netWorth, settings.baseCurrency),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "Total Assets",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.65f)
                                        )
                                        Text(
                                            text = FormatUtils.formatMoney(assets, settings.baseCurrency),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = IncomeGreenDark,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Liabilities",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.65f)
                                        )
                                        Text(
                                            text = FormatUtils.formatMoney(liabilities, settings.baseCurrency),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFFFF9B82),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                grouped.forEach { (type, list) ->
                    val sectionTotal = list.sumOf { it.balanceInBaseCurrency }
                    item(key = "group_${type.name}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = type.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = FormatUtils.formatMoney(sectionTotal, settings.baseCurrency),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (sectionTotal < 0) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (isWide) {
                        val chunkedRows = list.chunked(2)
                        items(chunkedRows, key = { row -> "${type.name}_${row.first().account.id}" }) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { item ->
                                    AccountSummaryCard(
                                        item = item,
                                        baseCurrency = settings.baseCurrency,
                                        accountNameMap = accountNameMap,
                                        onClick = {
                                            editingItem = item
                                            showAccountDialog = true
                                        },
                                        onTopUpNow = { acc, srcName ->
                                            viewModel.rechargeAccountNow(acc.id) { amount ->
                                                if (amount > 0.0) {
                                                    statusBanner = "Auto-recharged ${FormatUtils.formatMoney(amount, acc.currency)} from $srcName → ${acc.name}."
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        item(key = "group_card_${type.name}") {
                            SoftElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp
                            ) {
                                list.forEachIndexed { idx, item ->
                                    AccountSummaryCard(
                                        item = item,
                                        baseCurrency = settings.baseCurrency,
                                        accountNameMap = accountNameMap,
                                        onClick = {
                                            editingItem = item
                                            showAccountDialog = true
                                        },
                                        onTopUpNow = { acc, srcName ->
                                            viewModel.rechargeAccountNow(acc.id) { amount ->
                                                if (amount > 0.0) {
                                                    statusBanner = "Auto-recharged ${FormatUtils.formatMoney(amount, acc.currency)} from $srcName → ${acc.name}."
                                                }
                                            }
                                        },
                                        grouped = true
                                    )
                                    if (idx < list.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                        )
                                    }
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
            },
            onDeletePermanently = { account ->
                viewModel.deleteAccountPermanently(account.id)
                showAccountDialog = false
                statusBanner = "Deleted account '${account.name}'."
            }
        )
    }
}

@Composable
private fun AccountSummaryCard(
    item: AccountWithBalance,
    baseCurrency: String,
    accountNameMap: Map<Long, String>,
    onClick: () -> Unit,
    onTopUpNow: (AccountEntity, String) -> Unit,
    modifier: Modifier = Modifier,
    grouped: Boolean = false
) {
    val acc = item.account
    val accent = when (acc.type) {
        AccountType.E_WALLET -> CoralAccent
        AccountType.BANK -> EspressoInk
        AccountType.INVESTMENT -> ApricotHero
        AccountType.CASH -> Color(0xFF2B8A6E)
        else -> Color(acc.colorHex)
    }
    val subtitle = buildString {
        append(acc.currency)
        if (acc.autoRechargeSourceAccountId != null) {
            val srcName = accountNameMap[acc.autoRechargeSourceAccountId] ?: "Linked Bank"
            append(" · Auto-recharge from $srcName")
        }
        if (item.isLiveMarketValue && !acc.marketSymbol.isNullOrBlank()) {
            val friendlyLive = if (acc.marketSymbol.equals("XAU_GRAM", ignoreCase = true)) {
                "Live Gold (${acc.holdingUnits} g)"
            } else {
                "Live (${acc.marketSymbol} × ${acc.holdingUnits})"
            }
            append(" · $friendlyLive")
        } else if (acc.type == AccountType.INVESTMENT && !acc.marketSymbol.isNullOrBlank()) {
            val friendlyTicker = if (acc.marketSymbol.equals("XAU_GRAM", ignoreCase = true)) "Gold grams" else acc.marketSymbol
            append(" · Set units to sync ($friendlyTicker)")
        }
        if (!acc.includeInTotal) append(" · Excluded from Net Worth")
    }

    val cardBody: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (grouped) 12.dp else 14.dp, vertical = if (grouped) 10.dp else 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SquircleIconBadge(
                    icon = IconCatalog.resolve(acc.iconName),
                    tint = accent,
                    containerColor = accent.copy(alpha = 0.13f),
                    size = if (grouped) 38.dp else 44.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = acc.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
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
                    if (!acc.currency.equals(baseCurrency, ignoreCase = true)) {
                        Text(
                            text = "≈ ${FormatUtils.formatMoney(item.balanceInBaseCurrency, baseCurrency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (acc.autoRechargeSourceAccountId != null && item.currentBalance < acc.autoRechargeMinBalance - 0.005) {
                val srcName = accountNameMap[acc.autoRechargeSourceAccountId] ?: "Linked Bank"
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    onClick = { onTopUpNow(acc, srcName) },
                    shape = RoundedCornerShape(12.dp),
                    color = CoralSoft,
                    border = BorderStroke(1.dp, CoralAccent.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "Top Up Now from $srcName",
                        style = MaterialTheme.typography.labelLarge,
                        color = CoralAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }

    if (grouped) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            cardBody()
        }
    } else {
        SoftElevatedCard(
            modifier = modifier.fillMaxWidth(),
            onClick = onClick,
            cornerRadius = 20.dp
        ) {
            cardBody()
        }
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
    onArchive: (AccountEntity) -> Unit,
    onDeletePermanently: (AccountEntity) -> Unit
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
    var showRemoveConfirm by remember { mutableStateOf(false) }

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

    val isWide = rememberIsWideScreen()
    val showAutoRechargePicker = type == AccountType.E_WALLET || type == AccountType.OTHER || rechargeSourceId != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = if (isWide) 640.dp else 520.dp)
                .fillMaxWidth(0.92f),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (initial == null) "Add Account" else "Edit Account",
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
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Account Name (e.g., OCBC, PayLah)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
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
                            shape = RoundedCornerShape(14.dp),
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
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1.4f)
                        )
                        CurrencyDropdown(
                            selectedCurrency = currency,
                            onSelectCurrency = { currency = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (showAutoRechargePicker) {
                        ExposedDropdownMenuBox(
                            expanded = rechargeExpanded,
                            onExpandedChange = { rechargeExpanded = !rechargeExpanded }
                        ) {
                            OutlinedTextField(
                                value = rechargeSourceLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Auto-Recharge From Bank (if wallet < 0)") },
                                shape = RoundedCornerShape(14.dp),
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
                            label = { Text("Live Price Ticker (e.g. XAU_GRAM for Gold, ES3.SI, SPY)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = holdingUnitsText,
                            onValueChange = { holdingUnitsText = it },
                            label = { Text("Units / Shares / Gold Grams (for live sync)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (initial != null) {
                        TextButton(
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            onClick = { showRemoveConfirm = true }
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        EspressoPillButton(
                            text = "Save",
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
                                        colorHex = initial?.colorHex ?: 0xFFF05A30L,
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
                        )
                    }
                }
            }
        }
    }

    if (showRemoveConfirm && initial != null) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove '${initial.name}'?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose how you want to remove this account:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            showRemoveConfirm = false
                            onArchive(initial)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Hide Account (Archive & Keep History)")
                    }
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(14.dp),
                        onClick = {
                            showRemoveConfirm = false
                            onDeletePermanently(initial)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete Account Permanently")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
