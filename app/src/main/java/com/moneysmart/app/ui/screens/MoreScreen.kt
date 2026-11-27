package com.moneysmart.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneysmart.app.BuildConfig
import com.moneysmart.app.data.remote.AppUpdateChecker
import com.moneysmart.app.data.remote.AppUpdateInfo
import com.moneysmart.app.ui.components.EspressoPillButton
import com.moneysmart.app.ui.components.ResponsiveContentBox
import com.moneysmart.app.ui.components.SoftElevatedCard
import com.moneysmart.app.ui.components.SquircleIconBadge
import com.moneysmart.app.ui.components.WarmCurvedHeroHeader
import com.moneysmart.app.ui.components.rememberIsWideScreen
import com.moneysmart.app.ui.theme.ApricotHero
import com.moneysmart.app.ui.theme.CoralAccent
import com.moneysmart.app.ui.theme.EspressoInk
import com.moneysmart.app.ui.theme.IncomeGreen
import com.moneysmart.app.ui.theme.PeachSoft
import com.moneysmart.app.ui.theme.TransferBlue
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel

private data class MoreMenuItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val onClick: () -> Unit
)

@Composable
fun MoreScreen(
    viewModel: MoneySmartViewModel,
    onOpenBillSplitter: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenAiActivity: () -> Unit,
    onOpenAppAllowlist: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showUpdateDialog by remember { mutableStateOf(false) }

    val menuItems = listOf(
        MoreMenuItem(
            title = "Bill Splitter & Reimbursements",
            subtitle = "Split group bills and track friend repayments into any account",
            icon = Icons.Rounded.Groups,
            accent = CoralAccent,
            onClick = onOpenBillSplitter
        ),
        MoreMenuItem(
            title = "AI Activity & Test Simulator",
            subtitle = "Notification access status, captured alerts log & live AI parser tester",
            icon = Icons.Rounded.AutoAwesome,
            accent = EspressoInk,
            onClick = onOpenAiActivity
        ),
        MoreMenuItem(
            title = "Monitored Payment Apps",
            subtitle = "Choose which banking, card & e-wallet apps Money Smart reads",
            icon = Icons.Rounded.Apps,
            accent = ApricotHero,
            onClick = onOpenAppAllowlist
        ),
        MoreMenuItem(
            title = "Monthly Budgets",
            subtitle = "Set spending limits per category with 80% & 100% alerts",
            icon = Icons.Rounded.Savings,
            accent = IncomeGreen,
            onClick = onOpenBudgets
        ),
        MoreMenuItem(
            title = "Recurring & Subscriptions",
            subtitle = "Automate rent, salary & subscriptions",
            icon = Icons.Rounded.EventRepeat,
            accent = TransferBlue,
            onClick = onOpenRecurring
        ),
        MoreMenuItem(
            title = "Categories & Subcategories",
            subtitle = "Customize expense & income categories for AI auto-tagging",
            icon = Icons.Rounded.Category,
            accent = CoralAccent,
            onClick = onOpenCategories
        ),
        MoreMenuItem(
            title = "Backup & Restore (Drive / Local)",
            subtitle = "100% free JSON auto-backup, restore & CSV export",
            icon = Icons.Rounded.Backup,
            accent = IncomeGreen,
            onClick = onOpenBackup
        ),
        MoreMenuItem(
            title = "Settings & Security",
            subtitle = "Gemini free-tier API key, Base Currency, Theme & Biometric/PIN lock",
            icon = Icons.Rounded.Settings,
            accent = EspressoInk,
            onClick = onOpenSettings
        ),
        MoreMenuItem(
            title = "App Updates",
            subtitle = "Current v${BuildConfig.VERSION_NAME} · Check GitHub for new APK releases",
            icon = Icons.Rounded.SystemUpdate,
            accent = CoralAccent,
            onClick = { showUpdateDialog = true }
        )
    )

    val isWide = rememberIsWideScreen()
    val rows = if (isWide) menuItems.chunked(2) else menuItems.chunked(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WarmCurvedHeroHeader(
            title = "More & Tools",
            subtitle = "Bill splitting, AI automation, budgets, backups & security",
            heroColor = PeachSoft,
            maxWidth = if (isWide) 1040.dp else 900.dp
        )

        ResponsiveContentBox(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            maxWidth = if (isWide) 1040.dp else 900.dp
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rows) { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { item ->
                            SoftElevatedCard(
                                onClick = item.onClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                cornerRadius = 18.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SquircleIconBadge(
                                        icon = item.icon,
                                        tint = item.accent,
                                        containerColor = item.accent.copy(alpha = 0.13f),
                                        size = 40.dp
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = item.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (isWide && rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (showUpdateDialog) {
        AppUpdateDialog(
            viewModel = viewModel,
            onDismiss = { showUpdateDialog = false }
        )
    }
}

@Composable
private fun AppUpdateDialog(
    viewModel: MoneySmartViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var checkSeq by remember { mutableStateOf(0) }

    LaunchedEffect(checkSeq) {
        isLoading = true
        errorText = null
        val res = viewModel.checkForAppUpdate()
        res.fold(
            onSuccess = { info -> updateInfo = info },
            onFailure = { errorText = "Couldn't check for updates. Try again later." }
        )
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("App Updates", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Installed version: v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when {
                    isLoading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = CoralAccent)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Checking GitHub releases...")
                        }
                    }

                    errorText != null -> {
                        Text(
                            text = errorText!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CoralAccent
                        )
                    }

                    updateInfo != null -> {
                        val info = updateInfo!!
                        val badgeColor = if (info.isUpdateAvailable) CoralAccent else IncomeGreen
                        Surface(
                            color = badgeColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = if (info.isUpdateAvailable) {
                                    "Update Available: v${info.latestVersion}"
                                } else {
                                    "You're up to date (v${info.latestVersion})"
                                },
                                color = badgeColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        if (info.releaseNotes.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = info.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val info = updateInfo
            when {
                !isLoading && errorText != null -> {
                    EspressoPillButton(
                        text = "Retry",
                        onClick = { checkSeq++ }
                    )
                }

                !isLoading && info != null && info.isUpdateAvailable -> {
                    EspressoPillButton(
                        text = "Download APK",
                        onClick = { AppUpdateChecker.openApkOrReleasePage(context, info) }
                    )
                }

                !isLoading && info != null -> {
                    EspressoPillButton(
                        text = "Check Again",
                        onClick = { checkSeq++ }
                    )
                }
            }
        },
        dismissButton = {
            val info = updateInfo
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val urlToOpen = info?.releasePageUrl ?: AppUpdateChecker.FALLBACK_RELEASE_URL
                TextButton(onClick = { AppUpdateChecker.openUrl(context, urlToOpen) }) {
                    Text(if (info?.isUpdateAvailable == true) "Release Page" else "Open Releases")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
