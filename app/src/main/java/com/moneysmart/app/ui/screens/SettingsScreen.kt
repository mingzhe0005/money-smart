package com.moneysmart.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneysmart.app.BuildConfig
import com.moneysmart.app.data.local.ThemeMode
import com.moneysmart.app.data.remote.AppUpdateChecker
import com.moneysmart.app.data.remote.AppUpdateInfo
import com.moneysmart.app.data.remote.ProbedGeminiModel
import com.moneysmart.app.security.AppLockManager
import com.moneysmart.app.ui.components.EspressoPillButton
import com.moneysmart.app.ui.components.ResponsiveContentBox
import com.moneysmart.app.ui.components.SoftElevatedCard
import com.moneysmart.app.ui.components.WarmCurvedHeroHeader
import com.moneysmart.app.ui.components.WarmSegmentedPillBar
import com.moneysmart.app.ui.theme.CoralAccent
import com.moneysmart.app.ui.theme.IncomeGreen
import com.moneysmart.app.ui.theme.PeachSoft
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import com.moneysmart.app.util.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MoneySmartViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var apiKey by remember { mutableStateOf(settings.geminiApiKey) }
    var modelName by remember { mutableStateOf(settings.geminiModel) }
    var showApiKey by remember { mutableStateOf(false) }
    var apiTestMessage by remember { mutableStateOf<String?>(null) }
    var isTestingApi by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<ProbedGeminiModel>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }

    var rateSyncMessage by remember { mutableStateOf<String?>(null) }
    var confidenceSlider by remember { mutableFloatStateOf(settings.confidenceThreshold) }
    var showPinSetupDialog by remember { mutableStateOf(false) }

    var updateChecking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings.geminiApiKey) {
        apiKey = settings.geminiApiKey
    }
    LaunchedEffect(settings.geminiModel) {
        modelName = settings.geminiModel
    }
    LaunchedEffect(settings.confidenceThreshold) {
        confidenceSlider = settings.confidenceThreshold
    }

    val biometricAvailable = remember { AppLockManager.isBiometricAvailable(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        WarmCurvedHeroHeader(
            title = "Settings & Security",
            subtitle = "Gemini AI, currency rates, theme & lock",
            heroColor = PeachSoft,
            maxWidth = 860.dp,
            onNavigateBack = onNavigateBack
        )

        ResponsiveContentBox(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            maxWidth = 860.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Gemini AI Section
                SoftElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Gemini AI (Free Personal API Key)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Get a free API key from Google AI Studio (aistudio.google.com). Free tier requires no credit card and handles daily payment alerts easily.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Gemini API Key") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Toggle key visibility"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            label = { Text("Gemini Model (default: gemini-3.8-flash)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Auto-fallback enabled: if your chosen model is busy or unavailable, Money Smart automatically tries another working free-tier Flash model.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EspressoPillButton(
                                text = "Save Key",
                                onClick = {
                                    viewModel.saveGeminiConfig(apiKey, modelName)
                                    apiTestMessage = "Saved Gemini settings."
                                },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                enabled = !isTestingApi,
                                shape = RoundedCornerShape(18.dp),
                                onClick = {
                                    isTestingApi = true
                                    apiTestMessage = null
                                    viewModel.saveGeminiConfig(apiKey, modelName)
                                    scope.launch {
                                        val res = viewModel.testGeminiConnection(apiKey, modelName)
                                        apiTestMessage = res.fold(
                                            onSuccess = { msg -> msg },
                                            onFailure = { err -> "Connection failed: ${err.message}" }
                                        )
                                        isTestingApi = false
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = if (isTestingApi) "Testing..." else "Test Connection",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        OutlinedButton(
                            enabled = !isLoadingModels,
                            shape = RoundedCornerShape(999.dp),
                            onClick = {
                                isLoadingModels = true
                                apiTestMessage = null
                                scope.launch {
                                    val res = viewModel.fetchAvailableGeminiModels(apiKey)
                                    res.fold(
                                        onSuccess = { list ->
                                            availableModels = list
                                            if (list.isEmpty()) {
                                                apiTestMessage = "No free-tier models responded right now. Try again shortly."
                                            }
                                        },
                                        onFailure = { err ->
                                            apiTestMessage = "Failed to load models: ${err.message}"
                                        }
                                    )
                                    isLoadingModels = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isLoadingModels) "Checking Free Models..." else "Browse Usable Free Models")
                        }
                        if (availableModels.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                availableModels.forEach { candidate ->
                                    val label = if (candidate.isTemporarilyBusy) {
                                        "${candidate.name} (busy)"
                                    } else {
                                        candidate.name
                                    }
                                    FilterChip(
                                        selected = modelName.trim().equals(candidate.name, ignoreCase = true),
                                        onClick = {
                                            modelName = candidate.name
                                            viewModel.saveGeminiConfig(apiKey, candidate.name)
                                            apiTestMessage = if (candidate.isTemporarilyBusy) {
                                                "Selected ${candidate.name} (currently busy — auto-fallback will cover spikes)."
                                            } else {
                                                "Selected ${candidate.name}."
                                            }
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                        if (apiTestMessage != null) {
                            Text(
                                text = apiTestMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoralAccent,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // AI Auto-Record Behavior
                SoftElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "AI Recording Behavior",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Record Expenses", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Automatically add parsed payments and show a quick Edit/Undo alert",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.aiAutoRecordEnabled,
                                onCheckedChange = { viewModel.setAiPreferences(it, confidenceSlider) }
                            )
                        }
                        Text(
                            text = "Needs-Review Confidence Threshold: ${(confidenceSlider * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = confidenceSlider,
                            onValueChange = { confidenceSlider = it },
                            onValueChangeFinished = {
                                viewModel.setAiPreferences(settings.aiAutoRecordEnabled, confidenceSlider)
                            },
                            valueRange = 0.4f..0.95f
                        )
                    }
                }

                // Currency & Free Exchange Rates
                SoftElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Currency & Free Daily Rates",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        CurrencyDropdown(
                            selectedCurrency = settings.baseCurrency,
                            onSelectCurrency = { viewModel.setBaseCurrency(it) }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (settings.lastRateSyncTimestamp > 0L) {
                                    "Updated: ${FormatUtils.formatDateTime(settings.lastRateSyncTimestamp)}"
                                } else {
                                    "Rates not synced yet"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                shape = RoundedCornerShape(999.dp),
                                onClick = {
                                    scope.launch {
                                        val res = viewModel.syncExchangeRatesNow()
                                        rateSyncMessage = res.fold(
                                            onSuccess = { count -> "Synced $count currency rates (free API)." },
                                            onFailure = { err -> "Sync failed: ${err.message}" }
                                        )
                                    }
                                }
                            ) {
                                Text("Sync Rates")
                            }
                        }
                        if (rateSyncMessage != null) {
                            Text(
                                text = rateSyncMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoralAccent
                            )
                        }
                    }
                }

                // Appearance
                SoftElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Appearance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        WarmSegmentedPillBar(
                            options = ThemeMode.entries.map { it.displayName.replace(" Default", "") },
                            selectedIndex = ThemeMode.entries.indexOf(settings.themeMode).coerceAtLeast(0),
                            onSelect = { idx -> viewModel.setThemeMode(ThemeMode.entries[idx]) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // App Lock
                SoftElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Security",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("App Lock (PIN / Biometric)", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Protect access to your transactions and balances",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.appLockEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        showPinSetupDialog = true
                                    } else {
                                        viewModel.disableAppLock()
                                    }
                                }
                            )
                        }
                        if (settings.appLockEnabled && biometricAvailable) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Allow Fingerprint / Biometric Unlock")
                                Switch(
                                    checked = settings.biometricEnabled,
                                    onCheckedChange = { viewModel.setBiometricEnabled(it) }
                                )
                            }
                        }
                    }
                }

                // App Updates
                SoftElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "App Updates",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Installed version: v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EspressoPillButton(
                                text = if (updateChecking) "Checking..." else "Check for Updates",
                                enabled = !updateChecking,
                                onClick = {
                                    updateChecking = true
                                    updateError = null
                                    scope.launch {
                                        val res = viewModel.checkForAppUpdate()
                                        res.fold(
                                            onSuccess = { info -> updateInfo = info },
                                            onFailure = { err -> updateError = err.message ?: "Update check failed." }
                                        )
                                        updateChecking = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                shape = RoundedCornerShape(18.dp),
                                onClick = {
                                    val url = updateInfo?.releasePageUrl ?: AppUpdateChecker.FALLBACK_RELEASE_URL
                                    AppUpdateChecker.openUrl(context, url)
                                },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(
                                    text = "Releases Page",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (updateError != null) {
                            Text(
                                text = updateError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoralAccent
                            )
                        }
                        val info = updateInfo
                        if (info != null) {
                            val badgeColor = if (info.isUpdateAvailable) CoralAccent else IncomeGreen
                            Surface(
                                color = badgeColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = if (info.isUpdateAvailable) {
                                        "Update available: v${info.latestVersion}"
                                    } else {
                                        "Up to date (latest v${info.latestVersion})"
                                    },
                                    color = badgeColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            if (info.releaseNotes.isNotBlank()) {
                                Text(
                                    text = info.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (info.isUpdateAvailable) {
                                EspressoPillButton(
                                    text = "Download Update (APK)",
                                    onClick = { AppUpdateChecker.openUrl(context, info.apkDownloadUrl) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPinSetupDialog) {
        PinSetupDialog(
            biometricAvailable = biometricAvailable,
            onDismiss = { showPinSetupDialog = false },
            onSavePin = { pin, useBio ->
                viewModel.enablePinLock(pin, useBio)
                showPinSetupDialog = false
            }
        )
    }
}

@Composable
private fun PinSetupDialog(
    biometricAvailable: Boolean,
    onDismiss: () -> Unit,
    onSavePin: (String, Boolean) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var useBiometric by remember { mutableStateOf(biometricAvailable) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set App Lock PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                    label = { Text("Enter 4–6 digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it },
                    label = { Text("Confirm PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (biometricAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Biometric Unlock")
                        Switch(checked = useBiometric, onCheckedChange = { useBiometric = it })
                    }
                }
                if (err != null) {
                    Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length < 4 -> err = "PIN must be at least 4 digits."
                    pin != confirmPin -> err = "PINs do not match."
                    else -> onSavePin(pin, useBiometric)
                }
            }) { Text("Enable Lock") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
