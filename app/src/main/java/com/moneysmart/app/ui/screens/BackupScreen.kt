package com.moneysmart.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneysmart.app.ui.components.ResponsiveContentBox
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import com.moneysmart.app.util.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: MoneySmartViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.setBackupFolderUri(uri.toString())
            statusMessage = "Backup folder selected successfully."
        }
    }

    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isWorking = true
            statusMessage = null
            scope.launch {
                val res = viewModel.restoreJson(uri)
                statusMessage = res.fold(
                    onSuccess = { snap ->
                        "Restored ${snap.accounts.size} accounts, ${snap.categories.size} categories, and ${snap.transactions.size} transactions."
                    },
                    onFailure = { err -> "Restore failed: ${err.message}" }
                )
                isWorking = false
            }
        }
    }

    val importMoneyManagerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isWorking = true
            statusMessage = null
            scope.launch {
                val res = viewModel.importMoneyManagerFile(uri)
                statusMessage = res.fold(
                    onSuccess = { summary ->
                        "Imported ${summary.importedCount} transactions (${summary.createdAccounts} new accounts, ${summary.createdCategories} new categories, ${summary.skippedDuplicateCount} duplicates skipped). Base currency: ${summary.detectedBaseCurrency}. Your AI habit memory is now updated!"
                    },
                    onFailure = { err -> "Import failed: ${err.message}" }
                )
                isWorking = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        ResponsiveContentBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            maxWidth = 860.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Free Google Drive & Local Backup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Money Smart uses Android's Storage Access Framework (100% free, zero cloud fees). " +
                                "When choosing a backup folder, pick your Google Drive folder or a local folder on your device.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Backup Destination", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (settings.backupFolderUri.isBlank()) {
                                "No folder selected yet."
                            } else {
                                "Folder URI: ${settings.backupFolderUri}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (settings.lastBackupTimestamp > 0L) {
                                "Last backup: ${FormatUtils.formatDateTime(settings.lastBackupTimestamp)}"
                            } else {
                                "Last backup: Never"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text("Choose Google Drive / Local Folder")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Daily Auto-Backup", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    "Automatically saves a JSON snapshot every 24 hours",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.autoBackupEnabled,
                                enabled = settings.backupFolderUri.isNotBlank(),
                                onCheckedChange = { viewModel.setAutoBackupEnabled(it) }
                            )
                        }
                    }
                }

                Button(
                    enabled = !isWorking && settings.backupFolderUri.isNotBlank(),
                    onClick = {
                        isWorking = true
                        statusMessage = null
                        scope.launch {
                            val res = viewModel.backupJsonNow(Uri.parse(settings.backupFolderUri))
                            statusMessage = res.fold(
                                onSuccess = { name -> "Saved JSON backup: $name" },
                                onFailure = { err -> "Backup failed: ${err.message}" }
                            )
                            isWorking = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Rounded.Backup, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Backup JSON Now")
                }

                OutlinedButton(
                    enabled = !isWorking && settings.backupFolderUri.isNotBlank(),
                    onClick = {
                        isWorking = true
                        statusMessage = null
                        scope.launch {
                            val res = viewModel.exportCsvNow(Uri.parse(settings.backupFolderUri))
                            statusMessage = res.fold(
                                onSuccess = { name -> "Exported CSV: $name" },
                                onFailure = { err -> "CSV export failed: ${err.message}" }
                            )
                            isWorking = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Rounded.TableChart, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Export Transactions CSV")
                }

                if (settings.backupFolderUri.isBlank()) {
                    Text(
                        text = "Tip: Choose a backup folder above first to enable 'Backup JSON Now' and 'Export Transactions CSV'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    enabled = !isWorking,
                    onClick = { restoreFileLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Rounded.Restore, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Restore from JSON Backup File")
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Import from Money Manager (.xls / .xlsx / .csv)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Import your existing Money Manager Excel export from Google Drive or local storage. Accounts, categories, foreign currencies, and transfers are preserved and immediately used for AI habit learning.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            enabled = !isWorking,
                            onClick = { importMoneyManagerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text("Import Money Manager Excel")
                        }
                    }
                }

                if (isWorking) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                if (statusMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Text(
                            text = statusMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
