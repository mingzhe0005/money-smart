package com.moneysmart.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneysmart.app.data.local.entity.NotificationProcessStatus
import com.moneysmart.app.notification.PaymentNotificationListenerService
import com.moneysmart.app.notification.ProcessOutcome
import com.moneysmart.app.ui.components.EmptyStateCard
import com.moneysmart.app.ui.theme.ExpenseRed
import com.moneysmart.app.ui.theme.IncomeGreen
import com.moneysmart.app.ui.theme.TransferBlue
import com.moneysmart.app.ui.theme.WarningAmber
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import com.moneysmart.app.util.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiActivityScreen(
    viewModel: MoneySmartViewModel,
    onNavigateBack: () -> Unit,
    onOpenAllowlist: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTransaction: (Long) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val rawNotifications by viewModel.rawNotifications.collectAsStateWithLifecycle()

    var isListenerEnabled by remember {
        mutableStateOf(PaymentNotificationListenerService.isNotificationListenerEnabled(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isListenerEnabled = PaymentNotificationListenerService.isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var simApp by remember { mutableStateOf("DBS PayLah!") }
    var simTitle by remember { mutableStateOf("Payment Successful") }
    var simText by remember {
        mutableStateOf("You paid ${settings.baseCurrency} 12.50 to Starbucks Coffee using Main Bank.")
    }
    var simRunning by remember { mutableStateOf(false) }
    var simResultText by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Notification Capture", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (rawNotifications.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearNotificationLog() }) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear log")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isListenerEnabled && settings.geminiApiKey.isNotBlank()) {
                            IncomeGreen.copy(alpha = 0.12f)
                        } else {
                            WarningAmber.copy(alpha = 0.14f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isListenerEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.NotificationsOff,
                                contentDescription = null,
                                tint = if (isListenerEnabled) IncomeGreen else WarningAmber
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isListenerEnabled) {
                                    "Notification Listener Enabled"
                                } else {
                                    "Notification Listener Disabled"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Monitored apps: ${settings.allowedPackages.size} selected · Gemini API key: ${if (settings.geminiApiKey.isBlank()) "Not set" else "Configured"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                }
                            ) {
                                Text(if (isListenerEnabled) "System Access" else "Enable Access")
                            }
                            OutlinedButton(onClick = onOpenAllowlist) {
                                Icon(Icons.Rounded.Apps, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Select Apps (${settings.allowedPackages.size})")
                            }
                        }
                        if (settings.geminiApiKey.isBlank()) {
                            TextButton(onClick = onOpenSettings) {
                                Text("Add Free Gemini API Key in Settings →")
                            }
                        }
                    }
                }
            }

            item {
                Card {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Test AI Parser Simulator",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Paste any bank or e-wallet notification to test how Gemini classifies it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = simApp,
                            onValueChange = { simApp = it },
                            label = { Text("App Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = simTitle,
                            onValueChange = { simTitle = it },
                            label = { Text("Notification Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = simText,
                            onValueChange = { simText = it },
                            label = { Text("Notification Body") },
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = !simRunning,
                                onClick = {
                                    simRunning = true
                                    simResultText = null
                                    scope.launch {
                                        val res = viewModel.simulateAiNotification(simApp, simTitle, simText)
                                        simResultText = res.fold(
                                            onSuccess = { (parsed, _) ->
                                                "Classification: ${parsed.classification}\n" +
                                                    "Amount: ${parsed.amount} ${parsed.currency ?: ""}\n" +
                                                    "Merchant: ${parsed.merchant}\n" +
                                                    "Account ID: ${parsed.accountId}, Category ID: ${parsed.categoryId}\n" +
                                                    "Confidence: ${(parsed.confidence * 100).toInt()}%\n" +
                                                    "Reason: ${parsed.reason.orEmpty()}"
                                            },
                                            onFailure = { err -> "Error: ${err.message}" }
                                        )
                                        simRunning = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Preview Parse")
                            }
                            Button(
                                enabled = !simRunning,
                                onClick = {
                                    simRunning = true
                                    simResultText = null
                                    scope.launch {
                                        val outcome = viewModel.recordSimulatedNotification(simApp, simTitle, simText)
                                        simResultText = when (outcome) {
                                            is ProcessOutcome.Recorded ->
                                                "Recorded Tx #${outcome.transactionId} (${outcome.summary}) · Needs Review=${outcome.needsReview}"
                                            is ProcessOutcome.Duplicate ->
                                                "Skipped as duplicate of Tx #${outcome.existingTransactionId}"
                                            is ProcessOutcome.Ignored ->
                                                "Ignored: ${outcome.reason}"
                                            is ProcessOutcome.RetryableFailure ->
                                                "Temporary failure: ${outcome.message}"
                                            is ProcessOutcome.PermanentFailure ->
                                                "Failed: ${outcome.message}"
                                        }
                                        simRunning = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Simulate & Record")
                            }
                        }
                        if (simRunning) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                        if (simResultText != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = simResultText!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Captured Notifications (${rawNotifications.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (rawNotifications.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No captured notifications yet",
                        subtitle = "Once enabled for your banking/e-wallet apps, incoming payment alerts will appear here."
                    )
                }
            } else {
                items(rawNotifications, key = { it.id }) { raw ->
                    val statusColor = when (raw.status) {
                        NotificationProcessStatus.RECORDED -> IncomeGreen
                        NotificationProcessStatus.DUPLICATE -> TransferBlue
                        NotificationProcessStatus.IGNORED -> MaterialTheme.colorScheme.onSurfaceVariant
                        NotificationProcessStatus.FAILED -> ExpenseRed
                        NotificationProcessStatus.PENDING, NotificationProcessStatus.PROCESSING -> WarningAmber
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = raw.linkedTransactionId != null) {
                                raw.linkedTransactionId?.let(onOpenTransaction)
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = raw.appName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = statusColor.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = raw.status.displayName,
                                        color = statusColor,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (raw.title.isNotBlank()) {
                                Text(
                                    text = raw.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (raw.text.isNotBlank()) {
                                Text(
                                    text = raw.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = FormatUtils.formatDateTime(raw.postedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row {
                                    if (raw.linkedTransactionId != null) {
                                        TextButton(onClick = { onOpenTransaction(raw.linkedTransactionId) }) {
                                            Text("Open Tx #${raw.linkedTransactionId}")
                                        }
                                    }
                                    if (raw.status == NotificationProcessStatus.FAILED ||
                                        raw.status == NotificationProcessStatus.PENDING
                                    ) {
                                        TextButton(onClick = { viewModel.retryRawNotification(raw.id) }) {
                                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Retry")
                                        }
                                    }
                                }
                            }
                            if (!raw.errorMessage.isNullOrBlank()) {
                                Text(
                                    text = raw.errorMessage,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
