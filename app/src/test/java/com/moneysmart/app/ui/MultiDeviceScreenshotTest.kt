package com.moneysmart.app.ui

import android.os.Looper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneysmart.app.data.local.DefaultSeedData
import com.moneysmart.app.data.local.MoneySmartDatabase
import com.moneysmart.app.data.local.ThemeMode
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.data.local.entity.ExchangeRateEntity
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionSource
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.ui.screens.AccountsScreen
import com.moneysmart.app.ui.screens.AddEditTransactionScreen
import com.moneysmart.app.ui.screens.MoreScreen
import com.moneysmart.app.ui.screens.OnboardingScreen
import com.moneysmart.app.ui.screens.StatsScreen
import com.moneysmart.app.ui.screens.TransactionsScreen
import com.moneysmart.app.ui.theme.MoneySmartTheme
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import java.time.YearMonth
import java.time.ZoneId

data class DeviceProfile(
    val name: String,
    val qualifiers: String,
    val fontScale: Float
) {
    override fun toString(): String = name
}

private enum class ScreenTarget(val filePrefix: String) {
    TRANSACTIONS("transactions"),
    STATS("stats"),
    ACCOUNTS("accounts"),
    MORE("more"),
    ADD_EDIT_TX("add_edit_tx"),
    ONBOARDING("onboarding")
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [34], application = TestMoneySmartApp::class)
class MultiDeviceScreenshotTest(
    private val profile: DeviceProfile
) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun data(): List<Array<Any>> = listOf(
            arrayOf(DeviceProfile("compact_phone", "w360dp-h780dp-xhdpi", 1.0f)),
            arrayOf(DeviceProfile("standard_phone", "w412dp-h915dp-xxhdpi", 1.0f)),
            arrayOf(DeviceProfile("large_font_phone", "w360dp-h780dp-xhdpi", 1.3f)),
            arrayOf(DeviceProfile("wide_tablet", "w840dp-h1100dp-xhdpi", 1.0f))
        )
    }

    private val qualifierAndCleanupRule = object : TestWatcher() {
        override fun starting(description: Description) {
            RuntimeEnvironment.setQualifiers(profile.qualifiers)
        }

        override fun finished(description: Description) {
            val vmJob = if (::viewModel.isInitialized) {
                viewModel.viewModelScope.coroutineContext[Job]
            } else {
                null
            }
            viewModelStore?.clear()
            viewModelStore = null
            shadowOf(Looper.getMainLooper()).idle()
            runBlocking {
                withTimeoutOrNull(5_000) {
                    vmJob?.join()
                }
            }
            shadowOf(Looper.getMainLooper()).idle()
            MoneySmartDatabase.resetForTests()
        }
    }

    private val composeTestRule = createComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(qualifierAndCleanupRule).around(composeTestRule)

    private var viewModelStore: ViewModelStore? = null
    private lateinit var viewModel: MoneySmartViewModel
    private var seededExpenseId: Long = 0L

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<TestMoneySmartApp>()
        val container = app.container
        val db = container.database

        runBlocking {
            container.settingsDataStore.setBaseCurrency("SGD")
            container.settingsDataStore.setOnboardingCompleted(true)
            container.settingsDataStore.setAiCaptureEnabled(true)

            DefaultSeedData.seedDefaultsIfEmpty(db, "SGD")
            DefaultSeedData.reorganizeCategories(db)

            val accountDao = db.accountDao()
            val cashId = accountDao.insert(
                AccountEntity(
                    name = "Cash",
                    type = AccountType.CASH,
                    currency = "SGD",
                    initialBalance = 85.50,
                    iconName = "AccountBalanceWallet",
                    colorHex = 0xFF14B8A6L
                )
            )
            val bankId = accountDao.insert(
                AccountEntity(
                    name = "Main Bank",
                    type = AccountType.BANK,
                    currency = "SGD",
                    initialBalance = 3240.00,
                    iconName = "AccountBalance",
                    colorHex = 0xFF0D6E52L
                )
            )
            val walletId = accountDao.insert(
                AccountEntity(
                    name = "Travel Wallet",
                    type = AccountType.E_WALLET,
                    currency = "EUR",
                    initialBalance = 42.90,
                    iconName = "Smartphone",
                    colorHex = 0xFF7C3AEDL
                )
            )
            val cardId = accountDao.insert(
                AccountEntity(
                    name = "Everyday Card",
                    type = AccountType.CREDIT_CARD,
                    currency = "SGD",
                    initialBalance = 0.0,
                    iconName = "CreditCard",
                    colorHex = 0xFFEF4444L
                )
            )

            db.exchangeRateDao().upsert(
                ExchangeRateEntity(
                    baseCurrency = "SGD",
                    targetCurrency = "EUR",
                    rate = 0.68
                )
            )

            val categories = db.categoryDao().getAllCategories()
            val foodId = categories.firstOrNull { it.name.equals("Food", ignoreCase = true) && it.parentId == null }?.id
            val snacksId = categories.firstOrNull { it.name.equals("Snacks", ignoreCase = true) }?.id ?: foodId
            val transportId = categories.firstOrNull { it.name.equals("Transport", ignoreCase = true) && it.parentId == null }?.id
            val salaryId = categories.firstOrNull { it.name.equals("Salary", ignoreCase = true) }?.id

            val zoneId = ZoneId.systemDefault()
            val today = YearMonth.now().atDay(12)
            val noonMillis = today.atTime(12, 30).atZone(zoneId).toInstant().toEpochMilli()
            val morningMillis = today.atTime(9, 15).atZone(zoneId).toInstant().toEpochMilli()
            val prevDayMillis = today.minusDays(1).atTime(19, 45).atZone(zoneId).toInstant().toEpochMilli()
            val salaryMillis = today.minusDays(5).atTime(10, 0).atZone(zoneId).toInstant().toEpochMilli()

            val txDao = db.transactionDao()
            seededExpenseId = txDao.insert(
                TransactionEntity(
                    type = TransactionType.EXPENSE,
                    amount = 14.80,
                    currency = "SGD",
                    exchangeRateToBase = 1.0,
                    baseAmount = 14.80,
                    accountId = cardId,
                    categoryId = foodId,
                    merchant = "CENTRAL BENTO HOUSE STATION AVE 12",
                    note = "Team lunch bento",
                    timestamp = noonMillis,
                    source = TransactionSource.AI_NOTIFICATION,
                    aiConfidence = 0.94f
                )
            )
            txDao.insert(
                TransactionEntity(
                    type = TransactionType.EXPENSE,
                    amount = 4.50,
                    currency = "SGD",
                    exchangeRateToBase = 1.0,
                    baseAmount = 4.50,
                    accountId = cashId,
                    categoryId = snacksId,
                    merchant = "Corner Roastery",
                    note = "Iced latte",
                    timestamp = morningMillis,
                    source = TransactionSource.MANUAL
                )
            )
            txDao.insert(
                TransactionEntity(
                    type = TransactionType.EXPENSE,
                    amount = 18.20,
                    currency = "SGD",
                    exchangeRateToBase = 1.0,
                    baseAmount = 18.20,
                    accountId = bankId,
                    categoryId = transportId,
                    merchant = "City Ride",
                    note = "Evening ride home",
                    timestamp = prevDayMillis,
                    source = TransactionSource.AI_NOTIFICATION,
                    aiConfidence = 0.91f
                )
            )
            txDao.insert(
                TransactionEntity(
                    type = TransactionType.INCOME,
                    amount = 2850.00,
                    currency = "SGD",
                    exchangeRateToBase = 1.0,
                    baseAmount = 2850.00,
                    accountId = bankId,
                    categoryId = salaryId,
                    merchant = "Monthly Payroll",
                    note = "Monthly salary",
                    timestamp = salaryMillis,
                    source = TransactionSource.MANUAL
                )
            )
            txDao.insert(
                TransactionEntity(
                    type = TransactionType.TRANSFER,
                    amount = 100.00,
                    currency = "SGD",
                    exchangeRateToBase = 1.0,
                    baseAmount = 100.00,
                    accountId = bankId,
                    toAccountId = walletId,
                    merchant = "Wallet Top-up",
                    note = "Travel wallet reload",
                    timestamp = salaryMillis - 3_600_000L,
                    source = TransactionSource.MANUAL
                )
            )
        }

        val store = ViewModelStore()
        viewModelStore = store
        viewModel = ViewModelProvider(
            store,
            ViewModelProvider.AndroidViewModelFactory(app)
        )[MoneySmartViewModel::class.java]
    }

    private fun hasNodeWithText(text: String, substring: Boolean = false): Boolean {
        return composeTestRule.onAllNodes(hasText(text, substring = substring))
            .fetchSemanticsNodes()
            .isNotEmpty()
    }

    @Test
    fun captureAllScreensForProfile() {
        var currentScreen by mutableStateOf(ScreenTarget.TRANSACTIONS)

        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = profile.fontScale)
            ) {
                MoneySmartTheme(themeMode = ThemeMode.LIGHT) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        when (currentScreen) {
                            ScreenTarget.TRANSACTIONS -> TransactionsScreen(
                                viewModel = viewModel,
                                onAddTransaction = {},
                                onEditTransaction = {},
                                onBulkDeleted = {},
                                onOpenAiActivity = {},
                                onOpenSettings = {}
                            )
                            ScreenTarget.STATS -> StatsScreen(
                                viewModel = viewModel,
                                onTransactionClick = {}
                            )
                            ScreenTarget.ACCOUNTS -> AccountsScreen(
                                viewModel = viewModel,
                                onQuickTransfer = {}
                            )
                            ScreenTarget.MORE -> MoreScreen(
                                viewModel = viewModel,
                                onOpenBillSplitter = {},
                                onOpenBudgets = {},
                                onOpenRecurring = {},
                                onOpenAiActivity = {},
                                onOpenAppAllowlist = {},
                                onOpenCategories = {},
                                onOpenBackup = {},
                                onOpenSettings = {}
                            )
                            ScreenTarget.ADD_EDIT_TX -> AddEditTransactionScreen(
                                viewModel = viewModel,
                                transactionId = seededExpenseId,
                                initialType = TransactionType.EXPENSE,
                                onNavigateBack = {},
                                onDeleted = {}
                            )
                            ScreenTarget.ONBOARDING -> OnboardingScreen(
                                viewModel = viewModel,
                                onFinished = {}
                            )
                        }
                    }
                }
            }
        }

        val readyPredicates: Map<ScreenTarget, () -> Boolean> = mapOf(
            ScreenTarget.TRANSACTIONS to { hasNodeWithText("Team lunch bento") },
            ScreenTarget.STATS to { hasNodeWithText("37.50", substring = true) },
            ScreenTarget.ACCOUNTS to { hasNodeWithText("Main Bank") },
            ScreenTarget.MORE to { hasNodeWithText("Bill Splitter & Reimbursements") },
            ScreenTarget.ADD_EDIT_TX to {
                hasNodeWithText("Save Changes") && hasNodeWithText("Team lunch bento")
            },
            ScreenTarget.ONBOARDING to { hasNodeWithText("Welcome to Money Smart") }
        )

        for (target in ScreenTarget.entries) {
            currentScreen = target
            composeTestRule.waitForIdle()
            try {
                composeTestRule.waitUntil(timeoutMillis = 15_000) {
                    readyPredicates.getValue(target).invoke()
                }
            } catch (t: Throwable) {
                val txCount = viewModel.allTransactions.value.size
                val accCount = viewModel.accountsWithBalances.value.size
                val catCount = viewModel.categories.value.size
                val tree = runCatching { composeTestRule.onRoot().printToString() }.getOrElse { it.toString() }
                throw AssertionError(
                    "Timed out on ${target.name} [${profile.name}] (txs=$txCount, accs=$accCount, cats=$catCount)\n$tree",
                    t
                )
            }
            composeTestRule.waitForIdle()
            composeTestRule.onRoot().captureRoboImage(
                filePath = "src/test/snapshots/${target.filePrefix}_${profile.name}.png"
            )
        }
    }
}
