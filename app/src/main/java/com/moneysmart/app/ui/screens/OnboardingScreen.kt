package com.moneysmart.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneysmart.app.data.local.DefaultSeedData
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.ui.components.EspressoPillButton
import com.moneysmart.app.ui.components.GeminiApiKeyGuideCard
import com.moneysmart.app.ui.components.IconCatalog
import com.moneysmart.app.ui.components.ResponsiveContentBox
import com.moneysmart.app.ui.components.SoftElevatedCard
import com.moneysmart.app.ui.components.SquircleIconBadge
import com.moneysmart.app.ui.components.WarmCurvedHeroHeader
import com.moneysmart.app.ui.theme.CoralAccent
import com.moneysmart.app.ui.theme.CoralSoft
import com.moneysmart.app.ui.theme.EspressoInk
import com.moneysmart.app.ui.theme.IncomeGreen
import com.moneysmart.app.ui.theme.PeachSoft
import com.moneysmart.app.ui.theme.PeachTint
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel

enum class OnboardingFinishDestination {
    HOME,
    AI_SETUP,
    IMPORT_BACKUP
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    viewModel: MoneySmartViewModel,
    onFinished: (OnboardingFinishDestination) -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var selectedCurrency by remember { mutableStateOf("SGD") }
    var aiCaptureEnabled by remember { mutableStateOf(false) }

    val selectedTemplateIds = remember {
        mutableStateMapOf<String, Boolean>().apply {
            DefaultSeedData.starterAccountTemplates.forEach { t ->
                put(t.id, t.defaultSelected)
            }
        }
    }
    val initialBalanceInputs = remember {
        mutableStateMapOf<String, String>().apply {
            DefaultSeedData.starterAccountTemplates.forEach { t ->
                put(t.id, "")
            }
        }
    }

    fun finishOnboarding(destination: OnboardingFinishDestination, isSkip: Boolean = false) {
        val chosenAccounts = DefaultSeedData.starterAccountTemplates
            .filter { selectedTemplateIds[it.id] == true }
            .map { spec ->
                val rawBalance = initialBalanceInputs[spec.id]?.trim()?.toDoubleOrNull() ?: 0.0
                val balance = if (spec.type == AccountType.CREDIT_CARD && rawBalance > 0.0) {
                    -rawBalance
                } else {
                    rawBalance
                }
                AccountEntity(
                    name = spec.name,
                    type = spec.type,
                    currency = selectedCurrency,
                    initialBalance = balance,
                    iconName = spec.iconName,
                    colorHex = spec.colorHex
                )
            }
        val finalAiEnabled = when {
            isSkip -> false
            destination == OnboardingFinishDestination.AI_SETUP -> true
            else -> aiCaptureEnabled
        }
        viewModel.completeOnboarding(
            baseCurrency = selectedCurrency,
            starterAccounts = chosenAccounts,
            aiCaptureEnabled = finalAiEnabled,
            onDone = { onFinished(destination) }
        )
    }

    val stepTitle = when (step) {
        0 -> "Welcome to Money Smart"
        1 -> "Set Up Starter Accounts"
        else -> "Choose How to Track"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        WarmCurvedHeroHeader(
            title = stepTitle,
            subtitle = "Step ${step + 1} of 3 · Quick setup",
            heroColor = PeachSoft,
            maxWidth = 820.dp,
            actions = {
                TextButton(onClick = { finishOnboarding(OnboardingFinishDestination.HOME, isSkip = true) }) {
                    Text(
                        text = "Skip",
                        color = CoralAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )

        ResponsiveContentBox(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            maxWidth = 820.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (step) {
                        0 -> {
                            SoftElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Pick your main currency",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "All net worth totals and monthly summaries will convert into this currency using free daily exchange rates.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val popular = listOf("SGD", "MYR", "USD", "EUR", "GBP", "AUD", "JPY", "IDR")
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        popular.forEach { code ->
                                            FilterChip(
                                                selected = selectedCurrency == code,
                                                onClick = { selectedCurrency = code },
                                                label = { Text(code, fontWeight = FontWeight.SemiBold) }
                                            )
                                        }
                                    }
                                    CurrencyDropdown(
                                        selectedCurrency = selectedCurrency,
                                        onSelectCurrency = { selectedCurrency = it }
                                    )
                                }
                            }
                        }

                        1 -> {
                            Text(
                                text = "Toggle the accounts or wallets you use. Leave balance blank to start at $selectedCurrency 0.00 — you can edit or add more anytime.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            val hasPayLah = selectedTemplateIds["paylah"] == true
                            val hasDbsPosb = selectedTemplateIds["dbs_posb"] == true
                            if (hasPayLah && hasDbsPosb) {
                                Surface(
                                    color = PeachTint,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, CoralAccent.copy(alpha = 0.28f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SwapHoriz,
                                            contentDescription = null,
                                            tint = CoralAccent
                                        )
                                        Text(
                                            text = "PayLah! will automatically recharge from DBS / POSB if its balance goes below zero.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EspressoInk
                                        )
                                    }
                                }
                            }

                            DefaultSeedData.starterAccountTemplates.forEach { spec ->
                                val isChecked = selectedTemplateIds[spec.id] == true
                                val accent = Color(spec.colorHex)
                                SoftElevatedCard(
                                    onClick = { selectedTemplateIds[spec.id] = !isChecked },
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 18.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { selectedTemplateIds[spec.id] = it }
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            SquircleIconBadge(
                                                icon = IconCatalog.resolve(spec.iconName),
                                                tint = accent,
                                                containerColor = accent.copy(alpha = 0.13f),
                                                size = 38.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = spec.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = spec.type.displayName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (isChecked) {
                                            OutlinedTextField(
                                                value = initialBalanceInputs[spec.id] ?: "",
                                                onValueChange = { initialBalanceInputs[spec.id] = it },
                                                label = {
                                                    Text(
                                                        if (spec.type == AccountType.CREDIT_CARD) {
                                                            "Current amount owed ($selectedCurrency)"
                                                        } else {
                                                            "Starting balance ($selectedCurrency)"
                                                        }
                                                    )
                                                },
                                                placeholder = { Text("0.00") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 46.dp, end = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            TrackingOptionCard(
                                title = "Manual Tracking",
                                subtitle = "Clean & private — log expenses manually without notification permissions or API keys. You can enable AI later anytime.",
                                icon = Icons.Rounded.EditNote,
                                selected = !aiCaptureEnabled,
                                accent = IncomeGreen,
                                onClick = { aiCaptureEnabled = false }
                            )

                            TrackingOptionCard(
                                title = "AI Notification Auto-Capture",
                                subtitle = "Automatically read payment alerts from your chosen banking & wallet apps using your own free Google Gemini API key.",
                                icon = Icons.Rounded.AutoAwesome,
                                selected = aiCaptureEnabled,
                                accent = CoralAccent,
                                onClick = { aiCaptureEnabled = true }
                            )

                            if (aiCaptureEnabled) {
                                GeminiApiKeyGuideCard()
                            }

                            SoftElevatedCard(
                                onClick = { finishOnboarding(OnboardingFinishDestination.IMPORT_BACKUP) },
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SquircleIconBadge(
                                        icon = Icons.Rounded.Backup,
                                        tint = CoralAccent,
                                        containerColor = CoralSoft,
                                        size = 40.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Migrating from Money Manager?",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Tap here to finish setup and jump straight to Excel/CSV or JSON import.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (step > 0) {
                            OutlinedButton(
                                onClick = { step-- },
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Back")
                            }
                        }
                        EspressoPillButton(
                            text = when {
                                step < 2 -> "Next"
                                aiCaptureEnabled -> "Finish & Open AI Setup"
                                else -> "Start Using Money Smart"
                            },
                            onClick = {
                                if (step < 2) {
                                    step++
                                } else {
                                    val dest = if (aiCaptureEnabled) {
                                        OnboardingFinishDestination.AI_SETUP
                                    } else {
                                        OnboardingFinishDestination.HOME
                                    }
                                    finishOnboarding(dest)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackingOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SquircleIconBadge(
                icon = icon,
                tint = accent,
                containerColor = accent.copy(alpha = 0.14f),
                size = 44.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Selected",
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
