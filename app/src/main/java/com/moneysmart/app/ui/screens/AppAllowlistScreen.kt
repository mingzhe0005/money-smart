package com.moneysmart.app.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneysmart.app.ui.components.ResponsiveContentBox
import com.moneysmart.app.ui.components.SoftElevatedCard
import com.moneysmart.app.ui.components.WarmCurvedHeroHeader
import com.moneysmart.app.ui.theme.PeachSoft
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledAppEntry(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean
)

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
                    InstalledAppEntry(
                        packageName = info.packageName,
                        label = label,
                        isSystemApp = isSystem
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
            val systemOk = showSystemApps || !app.isSystemApp || selected ||
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WarmCurvedHeroHeader(
            title = "Monitored Payment Apps",
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search banking, card, e-wallet, or SMS app...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show all system apps", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = showSystemApps, onCheckedChange = { showSystemApps = it })
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isChecked = app.packageName in settings.allowedPackages
                            SoftElevatedCard(
                                onClick = { viewModel.toggleAllowedPackage(app.packageName, !isChecked) },
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 16.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            viewModel.toggleAllowedPackage(app.packageName, checked)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            text = app.packageName,
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
    }
}
