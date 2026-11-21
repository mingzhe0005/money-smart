package com.moneysmart.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.moneysmart.app.security.AppLockManager

@Composable
fun AppLockScreen(
    expectedHash: String,
    salt: String,
    biometricEnabled: Boolean,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var enteredPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun triggerBiometric() {
        if (activity != null && biometricEnabled && AppLockManager.isBiometricAvailable(context)) {
            AppLockManager.showBiometricPrompt(
                activity = activity,
                onSuccess = onUnlocked,
                onError = { /* User cancelled or fell back to PIN */ }
            )
        }
    }

    LaunchedEffect(biometricEnabled) {
        triggerBiometric()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Money Smart Locked",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Enter your PIN to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(6) { idx ->
                    val filled = idx < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9")
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { digit ->
                            FilledTonalButton(
                                onClick = {
                                    if (enteredPin.length < 6) {
                                        enteredPin += digit
                                        errorMsg = null
                                        if (enteredPin.length >= 4 &&
                                            AppLockManager.verifyPin(enteredPin, expectedHash, salt)
                                        ) {
                                            onUnlocked()
                                        }
                                    }
                                },
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape
                            ) {
                                Text(digit, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (biometricEnabled && AppLockManager.isBiometricAvailable(context)) {
                        FilledTonalButton(
                            onClick = { triggerBiometric() },
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Rounded.Fingerprint, contentDescription = "Biometric unlock")
                        }
                    } else {
                        Spacer(modifier = Modifier.size(72.dp))
                    }

                    FilledTonalButton(
                        onClick = {
                            if (enteredPin.length < 6) {
                                enteredPin += "0"
                                errorMsg = null
                                if (enteredPin.length >= 4 &&
                                    AppLockManager.verifyPin(enteredPin, expectedHash, salt)
                                ) {
                                    onUnlocked()
                                }
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape
                    ) {
                        Text("0", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }

                    FilledTonalButton(
                        onClick = {
                            if (enteredPin.isNotEmpty()) {
                                enteredPin = enteredPin.dropLast(1)
                                errorMsg = null
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Backspace")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                enabled = enteredPin.length >= 4,
                onClick = {
                    if (AppLockManager.verifyPin(enteredPin, expectedHash, salt)) {
                        onUnlocked()
                    } else {
                        errorMsg = "Incorrect PIN. Try again."
                        enteredPin = ""
                    }
                },
                modifier = Modifier.width(220.dp)
            ) {
                Text("Unlock")
            }
            if (enteredPin.isNotEmpty()) {
                TextButton(onClick = { enteredPin = ""; errorMsg = null }) {
                    Text("Clear")
                }
            }
        }
    }
}
