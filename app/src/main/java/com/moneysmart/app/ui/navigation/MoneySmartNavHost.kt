package com.moneysmart.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.ui.screens.AccountsScreen
import com.moneysmart.app.ui.screens.AddEditTransactionScreen
import com.moneysmart.app.ui.screens.AiActivityScreen
import com.moneysmart.app.ui.screens.AppAllowlistScreen
import com.moneysmart.app.ui.screens.BackupScreen
import com.moneysmart.app.ui.screens.BudgetsScreen
import com.moneysmart.app.ui.screens.CategoriesScreen
import com.moneysmart.app.ui.screens.MoreScreen
import com.moneysmart.app.ui.screens.RecurringScreen
import com.moneysmart.app.ui.screens.SettingsScreen
import com.moneysmart.app.ui.screens.StatsScreen
import com.moneysmart.app.ui.screens.TransactionsScreen
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import kotlinx.coroutines.launch

object Routes {
    const val TRANSACTIONS = "transactions"
    const val STATS = "stats"
    const val ACCOUNTS = "accounts"
    const val MORE = "more"
    const val ADD_EDIT_TRANSACTION = "add_edit_transaction?txId={txId}&type={type}"
    const val BUDGETS = "budgets"
    const val RECURRING = "recurring"
    const val AI_ACTIVITY = "ai_activity"
    const val APP_ALLOWLIST = "app_allowlist"
    const val CATEGORIES = "categories"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"

    fun addEditTransaction(
        txId: Long? = null,
        initialType: TransactionType = TransactionType.EXPENSE
    ): String {
        val idArg = if (txId != null && txId > 0L) txId else -1L
        return "add_edit_transaction?txId=$idArg&type=${initialType.name}"
    }
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun MoneySmartNavHost(
    viewModel: MoneySmartViewModel,
    deepLinkTransactionId: Long?,
    onConsumedDeepLink: () -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val bottomTabs = listOf(
        BottomTab(Routes.TRANSACTIONS, "Transactions", Icons.AutoMirrored.Rounded.ReceiptLong),
        BottomTab(Routes.STATS, "Stats", Icons.Rounded.PieChart),
        BottomTab(Routes.ACCOUNTS, "Accounts", Icons.Rounded.AccountBalanceWallet),
        BottomTab(Routes.MORE, "More", Icons.Rounded.MoreHoriz)
    )
    val bottomRoutes = bottomTabs.map { it.route }.toSet()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(deepLinkTransactionId) {
        if (deepLinkTransactionId != null && deepLinkTransactionId > 0L) {
            navController.navigate(Routes.addEditTransaction(deepLinkTransactionId))
            onConsumedDeepLink()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute in bottomRoutes) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        val selected = backStackEntry?.destination?.hierarchy?.any {
                            it.route == tab.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Routes.TRANSACTIONS
            ) {
                composable(Routes.TRANSACTIONS) {
                    TransactionsScreen(
                        viewModel = viewModel,
                        onAddTransaction = { navController.navigate(Routes.addEditTransaction()) },
                        onEditTransaction = { id -> navController.navigate(Routes.addEditTransaction(id)) },
                        onBulkDeleted = { deletedIds ->
                            scope.launch {
                                val count = deletedIds.size
                                val result = snackbarHostState.showSnackbar(
                                    message = "Deleted $count transaction${if (count == 1) "" else "s"}",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restoreTransactions(deletedIds)
                                }
                            }
                        },
                        onOpenAiActivity = { navController.navigate(Routes.AI_ACTIVITY) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                    )
                }

                composable(Routes.STATS) {
                    StatsScreen(viewModel = viewModel)
                }

                composable(Routes.ACCOUNTS) {
                    AccountsScreen(
                        viewModel = viewModel,
                        onQuickTransfer = {
                            navController.navigate(
                                Routes.addEditTransaction(initialType = TransactionType.TRANSFER)
                            )
                        }
                    )
                }

                composable(Routes.MORE) {
                    MoreScreen(
                        onOpenBudgets = { navController.navigate(Routes.BUDGETS) },
                        onOpenRecurring = { navController.navigate(Routes.RECURRING) },
                        onOpenAiActivity = { navController.navigate(Routes.AI_ACTIVITY) },
                        onOpenAppAllowlist = { navController.navigate(Routes.APP_ALLOWLIST) },
                        onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                        onOpenBackup = { navController.navigate(Routes.BACKUP) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                    )
                }

                composable(
                    route = Routes.ADD_EDIT_TRANSACTION,
                    arguments = listOf(
                        navArgument("txId") {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                        navArgument("type") {
                            type = NavType.StringType
                            defaultValue = TransactionType.EXPENSE.name
                        }
                    )
                ) { entry ->
                    val txId = entry.arguments?.getLong("txId")?.takeIf { it > 0L }
                    val initialTypeName = entry.arguments?.getString("type")
                    val initialType = runCatching {
                        TransactionType.valueOf(initialTypeName ?: TransactionType.EXPENSE.name)
                    }.getOrDefault(TransactionType.EXPENSE)
                    AddEditTransactionScreen(
                        viewModel = viewModel,
                        transactionId = txId,
                        initialType = initialType,
                        onNavigateBack = { navController.popBackStack() },
                        onDeleted = { deletedId ->
                            navController.popBackStack()
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Transaction deleted",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restoreTransaction(deletedId)
                                }
                            }
                        }
                    )
                }

                composable(Routes.BUDGETS) {
                    BudgetsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.RECURRING) {
                    RecurringScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.AI_ACTIVITY) {
                    AiActivityScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenAllowlist = { navController.navigate(Routes.APP_ALLOWLIST) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenTransaction = { id -> navController.navigate(Routes.addEditTransaction(id)) }
                    )
                }

                composable(Routes.APP_ALLOWLIST) {
                    AppAllowlistScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.CATEGORIES) {
                    CategoriesScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.BACKUP) {
                    BackupScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
