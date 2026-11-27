package com.moneysmart.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneysmart.app.data.remote.AppUpdateChecker
import com.moneysmart.app.notification.TransactionAlertNotifier
import com.moneysmart.app.ui.components.EspressoPillButton
import com.moneysmart.app.ui.navigation.MoneySmartNavHost
import com.moneysmart.app.ui.navigation.Routes
import com.moneysmart.app.ui.screens.AppLockScreen
import com.moneysmart.app.ui.screens.OnboardingFinishDestination
import com.moneysmart.app.ui.screens.OnboardingScreen
import com.moneysmart.app.ui.theme.MoneySmartTheme
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    private var deepLinkTransactionId by mutableStateOf<Long?>(null)
    private var isUnlockedThisSession by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        requestNotificationPermissionIfNeeded()

        val container = MoneySmartApp.container(this)
        val settingsFlow = container.settingsDataStore.settingsFlow

        setContent {
            val loadedSettings by settingsFlow.collectAsState(initial = null)
            val appSettings = loadedSettings
            var onboardingChecked by remember { mutableStateOf(false) }
            var initialNavigateRoute by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(appSettings?.onboardingCompleted) {
                if (appSettings != null && !appSettings.onboardingCompleted && !onboardingChecked) {
                    val hasExistingData = withContext(Dispatchers.IO) {
                        container.repository.hasAnyAccounts()
                    }
                    if (hasExistingData) {
                        container.settingsDataStore.setOnboardingCompleted(true)
                    }
                    onboardingChecked = true
                }
            }

            MoneySmartTheme(themeMode = appSettings?.themeMode ?: com.moneysmart.app.data.local.ThemeMode.SYSTEM) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (appSettings == null || (!appSettings.onboardingCompleted && !onboardingChecked)) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (!appSettings.onboardingCompleted) {
                        val vm: MoneySmartViewModel = viewModel()
                        OnboardingScreen(
                            viewModel = vm,
                            onFinished = { destination ->
                                initialNavigateRoute = when (destination) {
                                    OnboardingFinishDestination.AI_SETUP -> Routes.SETTINGS
                                    OnboardingFinishDestination.IMPORT_BACKUP -> Routes.BACKUP
                                    OnboardingFinishDestination.HOME -> null
                                }
                            }
                        )
                    } else if (appSettings.appLockEnabled &&
                        appSettings.pinHash.isNotBlank() &&
                        !isUnlockedThisSession
                    ) {
                        AppLockScreen(
                            expectedHash = appSettings.pinHash,
                            salt = appSettings.pinSalt,
                            biometricEnabled = appSettings.biometricEnabled,
                            onUnlocked = { isUnlockedThisSession = true }
                        )
                    } else {
                        val vm: MoneySmartViewModel = viewModel()
                        val pendingAutoUpdate by container.appUpdateCoordinator.autoUpdatePrompt.collectAsState()
                        MoneySmartNavHost(
                            viewModel = vm,
                            deepLinkTransactionId = deepLinkTransactionId,
                            onConsumedDeepLink = { deepLinkTransactionId = null },
                            initialNavigateRoute = initialNavigateRoute,
                            onConsumedInitialRoute = { initialNavigateRoute = null }
                        )
                        pendingAutoUpdate?.let { info ->
                            AutoUpdateAvailableDialog(
                                info = info,
                                onUpdate = {
                                    container.appUpdateCoordinator.clearAutoPrompt()
                                    AppUpdateChecker.openApkOrReleasePage(this@MainActivity, info)
                                },
                                onLater = {
                                    container.appUpdateCoordinator.dismissForLater(info.latestVersion)
                                },
                                onDismissRequest = {
                                    container.appUpdateCoordinator.clearAutoPrompt()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val container = MoneySmartApp.container(this)
        container.appUpdateCoordinator.onAppForegrounded()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                container.notificationProcessor
                    .processAllPendingNotifications(includeFailed = false)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val txId = intent?.getLongExtra(TransactionAlertNotifier.EXTRA_EDIT_TRANSACTION_ID, -1L) ?: -1L
        if (txId > 0L) {
            deepLinkTransactionId = txId
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AutoUpdateAvailableDialog(
    info: com.moneysmart.app.data.remote.AppUpdateInfo,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text("Update available", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Money Smart ${info.latestVersion} is available.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (info.releaseNotes.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                    ) {
                        Text(
                            text = info.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        },
        confirmButton = {
            EspressoPillButton(
                text = "Update",
                onClick = onUpdate
            )
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("Later")
            }
        }
    )
}
