package com.moneysmart.app.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneysmart.app.ui.components.ResponsiveContentBox
import com.moneysmart.app.ui.components.SoftElevatedCard
import com.moneysmart.app.ui.components.SquircleIconBadge
import com.moneysmart.app.ui.components.WarmCurvedHeroHeader
import com.moneysmart.app.ui.theme.CoralAccent
import com.moneysmart.app.ui.theme.CoralSoft
import com.moneysmart.app.ui.theme.PeachSoft
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledAppEntry(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val isSuggested: Boolean,
    val icon: ImageBitmap?
)

private fun isSuggestedPaymentApp(packageName: String, label: String): Boolean {
    val pkg = packageName.lowercase()
    val lbl = label.lowercase()
    val keywords = listOf(
        "ocbc", "dbs", "posb", "paylah", "maribank", "trust",
        "uob", "samsungpay", "spay", "samsung wallet", "walletnfcrel", "google wallet",
        "youapp", "youtrip", "transferwise", "wise", "grab", "shopee",
        "revolut", "gxs", "lifesg", "citi", "hsbc", "sc.com", "standard chartered",
        "maybank", "atome", "paypal", "alipay", "wechat"
    )
    return keywords.any { kw -> pkg.contains(kw) || lbl.contains(kw) }
}

@Composable
fun AppAllowlistScreen(
    viewModel: MoneySmartViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<InstalledAppEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val ownPkg = context.packageName
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != ownPkg }
                .map { info ->
                    val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                    val label = runCatching { pm.getApplicationLabel(info).toString() }
                        .getOrDefault(info.packageName)
                    val icon = runCatching {
                        pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap()
                    }.getOrNull()
                    InstalledAppEntry(
                        packageName = info.packageName,
                        label = label,
                        isSystemApp = isSystem,
                        isSuggested = isSuggestedPaymentApp(info.packageName, label),
                        icon = icon
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
        isLoading = false
    }

    val filteredApps = remember(installedApps, searchQuery, showSystemApps, settings.allowedPackages) {
        val q = searchQuery.trim()
        installedApps.filter { app ->
            val selected = app.packageName in settings.allowedPackages
            val systemOk = showSystemApps || !app.isSystemApp || selected || app.isSuggested ||
                app.label.contains("wallet", true) ||
                app.label.contains("pay", true) ||
                app.label.contains("messages", true) ||
                app.label.contains("bank", true)
            val queryOk = q.isEmpty() ||
                app.label.contains(q, ignoreCase = true) ||
                app.packageName.contains(q, ignoreCase = true)
            systemOk && queryOk
        }.sortedWith(
            compareByDescending<InstalledAppEntry> { it.packageName in settings.allowedPackages }
                .thenBy { it.label.lowercase() }
        )
    }

    val suggestedApps = remember(filteredApps) { filteredApps.filter { it.isSuggested } }
    val otherApps = remember(filteredApps) { filteredApps.filter { !it.isSuggested } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WarmCurvedHeroHeader(
            title = "Monitored Apps",
            subtitle = "${settings.allowedPackages.size} selected · Private allowlist",
            heroColor = PeachSoft,
            maxWidth = 900.dp,
            onNavigateBack = onNavigateBack
        )

        ResponsiveContentBox(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            maxWidth = 900.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Only notifications from the apps you check below are sent to Gemini AI for expense extraction.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search banking, card, e-wallet, or SMS app...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show all system apps", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = showSystemApps, onCheckedChange = { showSystemApps = it })
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CoralAccent)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (searchQuery.isBlank() && suggestedApps.isNotEmpty()) {
                            item(key = "header_suggested") {
                                val hasUnchecked = suggestedApps.any { it.packageName !in settings.allowedPackages }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = CoralAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Suggested Payment Apps",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (hasUnchecked) {
                                        TextButton(
                                            onClick = {
                                                suggestedApps
                                                    .filter { it.packageName !in settings.allowedPackages }
                                                    .forEach { viewModel.toggleAllowedPackage(it.packageName, true) }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text("Select All", color = CoralAccent, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            items(suggestedApps, key = { "sug_${it.packageName}" }) { app ->
                                AppAllowlistRow(
                                    app = app,
                                    isChecked = app.packageName in settings.allowedPackages,
                                    onToggle = { checked -> viewModel.toggleAllowedPackage(app.packageName, checked) }
                                )
                            }
                            if (otherApps.isNotEmpty()) {
                                item(key = "header_all") {
                                    Text(
                                        text = "All Installed Apps",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
                                    )
                                }
                            }
                        }

                        val listToRender = if (searchQuery.isBlank() && suggestedApps.isNotEmpty()) otherApps else filteredApps
                        items(listToRender, key = { it.packageName }) { app ->
                            AppAllowlistRow(
                                app = app,
                                isChecked = app.packageName in settings.allowedPackages,
                                onToggle = { checked -> viewModel.toggleAllowedPackage(app.packageName, checked) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppAllowlistRow(
    app: InstalledAppEntry,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    SoftElevatedCard(
        onClick = { onToggle(!isChecked) },
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = onToggle
            )
            Spacer(modifier = Modifier.width(6.dp))
            if (app.icon != null) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                SquircleIconBadge(
                    icon = Icons.Rounded.Apps,
                    tint = CoralAccent,
                    containerColor = CoralSoft,
                    size = 38.dp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (app.isSuggested) {
                        Surface(
                            color = CoralSoft,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "Suggested",
                                style = MaterialTheme.typography.labelSmall,
                                color = CoralAccent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
