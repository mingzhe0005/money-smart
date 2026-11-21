package com.moneysmart.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class MoreMenuItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenBudgets: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenAiActivity: () -> Unit,
    onOpenAppAllowlist: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val menuItems = listOf(
        MoreMenuItem(
            title = "AI Activity & Test Simulator",
            subtitle = "Notification access status, captured alerts log & live AI parser tester",
            icon = Icons.Rounded.AutoAwesome,
            onClick = onOpenAiActivity
        ),
        MoreMenuItem(
            title = "Monitored Payment Apps",
            subtitle = "Choose which banking, card & e-wallet apps Money Smart reads",
            icon = Icons.Rounded.Apps,
            onClick = onOpenAppAllowlist
        ),
        MoreMenuItem(
            title = "Monthly Budgets",
            subtitle = "Set spending limits per category with 80% & 100% alerts",
            icon = Icons.Rounded.Savings,
            onClick = onOpenBudgets
        ),
        MoreMenuItem(
            title = "Recurring & Subscriptions",
            subtitle = "Automate rent, salary & subscriptions",
            icon = Icons.Rounded.EventRepeat,
            onClick = onOpenRecurring
        ),
        MoreMenuItem(
            title = "Categories & Subcategories",
            subtitle = "Customize expense & income categories for AI auto-tagging",
            icon = Icons.Rounded.Category,
            onClick = onOpenCategories
        ),
        MoreMenuItem(
            title = "Backup & Restore (Google Drive / Local)",
            subtitle = "100% free JSON auto-backup, restore & CSV export",
            icon = Icons.Rounded.Backup,
            onClick = onOpenBackup
        ),
        MoreMenuItem(
            title = "Settings & Security",
            subtitle = "Gemini free-tier API key, Base Currency, Theme & Biometric/PIN lock",
            icon = Icons.Rounded.Settings,
            onClick = onOpenSettings
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("More & Tools", fontWeight = FontWeight.Bold) })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(menuItems) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = item.onClick)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
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
        }
    }
}
