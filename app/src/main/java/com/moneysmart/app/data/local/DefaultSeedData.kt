package com.moneysmart.app.data.local

import androidx.room.withTransaction
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.CategoryType
import com.moneysmart.app.data.remote.MarketPriceApiClient
import com.moneysmart.app.data.repository.LedgerCalculations
import kotlin.math.roundToLong

object DefaultSeedData {

    data class SnapshotAccountSpec(
        val name: String,
        val type: AccountType,
        val currency: String,
        val targetBalance: Double,
        val iconName: String,
        val colorHex: Long,
        val defaultMarketSymbol: String? = null
    )

    val snapshotAccountSpecs: List<SnapshotAccountSpec> = listOf(
        // Cash
        SnapshotAccountSpec("Coins", AccountType.CASH, "SGD", 2.20, "AccountBalanceWallet", 0xFF14B8A6L),
        SnapshotAccountSpec("Notes", AccountType.CASH, "SGD", 9.00, "Payments", 0xFF0D6E52L),
        SnapshotAccountSpec("Euro Notes", AccountType.CASH, "EUR", 18.50, "Payments", 0xFF2563EBL),
        SnapshotAccountSpec("Euros Coins", AccountType.CASH, "EUR", 15.20, "AccountBalanceWallet", 0xFF0EA5E9L),
        // Banks / Accounts / Savings
        SnapshotAccountSpec("OCBC", AccountType.BANK, "SGD", 225.29, "AccountBalance", 0xFFDC2626L),
        SnapshotAccountSpec("Maribank", AccountType.BANK, "SGD", 0.00, "AccountBalance", 0xFFF59E0BL),
        SnapshotAccountSpec("Trust", AccountType.BANK, "SGD", 1.45, "AccountBalance", 0xFF0EA5E9L),
        SnapshotAccountSpec("POSB", AccountType.BANK, "SGD", 509.66, "AccountBalance", 0xFF2563EBL),
        // Wallets & Prepaid
        SnapshotAccountSpec("PayLah", AccountType.E_WALLET, "SGD", -10.90, "Smartphone", 0xFFEF4444L),
        SnapshotAccountSpec("LifeSG", AccountType.E_WALLET, "SGD", 0.00, "CardGiftcard", 0xFF0D6E52L),
        SnapshotAccountSpec("YouTrip-SGD", AccountType.E_WALLET, "SGD", 0.00, "CreditCard", 0xFF7C3AEDL),
        SnapshotAccountSpec("Youtrip - Euros", AccountType.E_WALLET, "EUR", 48.09, "CreditCard", 0xFF6366F1L),
        SnapshotAccountSpec("Wise", AccountType.E_WALLET, "SGD", 2.62, "AccountBalanceWallet", 0xFF10B981L),
        SnapshotAccountSpec("Timbre", AccountType.E_WALLET, "SGD", 7.86, "Smartphone", 0xFFF59E0BL),
        // Investments
        SnapshotAccountSpec("Trust investment", AccountType.INVESTMENT, "SGD", 1016.28, "TrendingUp", 0xFF2563EBL),
        SnapshotAccountSpec("Investment with Dad", AccountType.INVESTMENT, "SGD", 10000.00, "TrendingUp", 0xFF0D6E52L),
        SnapshotAccountSpec("Mari Invest", AccountType.INVESTMENT, "SGD", 913.54, "TrendingUp", 0xFF059669L),
        SnapshotAccountSpec(
            name = "Mari Gold",
            type = AccountType.INVESTMENT,
            currency = "SGD",
            targetBalance = 1489.45,
            iconName = "TrendingUp",
            colorHex = 0xFFD97706L,
            defaultMarketSymbol = MarketPriceApiClient.GOLD_GRAM_SYMBOL
        )
    )

    suspend fun seedDefaultsIfEmpty(db: MoneySmartDatabase, baseCurrency: String = "SGD") {
        db.withTransaction {
            val existingAccounts = db.accountDao().getAllAccounts()
            val existingCategories = db.categoryDao().getAllCategories()

            if (existingAccounts.isEmpty()) {
                val accounts = snapshotAccountSpecs.map { spec ->
                    AccountEntity(
                        name = spec.name,
                        type = spec.type,
                        currency = spec.currency,
                        initialBalance = spec.targetBalance,
                        iconName = spec.iconName,
                        colorHex = spec.colorHex,
                        marketSymbol = spec.defaultMarketSymbol
                    )
                }
                db.accountDao().insertAll(accounts)
                linkPayLahToPosbInternal(db)
            }

            if (existingCategories.isEmpty()) {
                val categoryDao = db.categoryDao()
                var order = 1

                suspend fun addCategory(
                    name: String,
                    type: CategoryType,
                    iconName: String,
                    colorHex: Long
                ) {
                    categoryDao.insert(
                        CategoryEntity(
                            name = name,
                            type = type,
                            parentId = null,
                            iconName = iconName,
                            colorHex = colorHex,
                            sortOrder = order++
                        )
                    )
                }

                // Expense categories matching user's Money Manager export
                addCategory("🍜 Food", CategoryType.EXPENSE, "Restaurant", 0xFFEF4444L)
                addCategory("🍿Snacks", CategoryType.EXPENSE, "LocalCafe", 0xFFF59E0BL)
                addCategory("🚖 Transport", CategoryType.EXPENSE, "DirectionsCar", 0xFF0EA5E9L)
                addCategory("👬🏻 Social Life", CategoryType.EXPENSE, "ShoppingBag", 0xFFEC4899L)
                addCategory("with lulu ❤️", CategoryType.EXPENSE, "CardGiftcard", 0xFFF43F5EL)
                addCategory("🎁 Gift", CategoryType.EXPENSE, "CardGiftcard", 0xFF8B5CF6L)
                addCategory("📙 Education", CategoryType.EXPENSE, "School", 0xFF3B82F6L)
                addCategory("🖼 Culture", CategoryType.EXPENSE, "Movie", 0xFF14B8A6L)
                addCategory("🛏 accomodation", CategoryType.EXPENSE, "Home", 0xFF6366F1L)
                addCategory("🧘🏼 Health", CategoryType.EXPENSE, "LocalHospital", 0xFF10B981L)
                addCategory("🧥 Apparel", CategoryType.EXPENSE, "ShoppingBag", 0xFFA855F7L)
                addCategory("🪑 Household", CategoryType.EXPENSE, "Home", 0xFF84CC16L)
                addCategory("Other", CategoryType.EXPENSE, "MoreHoriz", 0xFF64748BL)

                // Income categories matching user's Money Manager export
                addCategory("💰 Salary", CategoryType.INCOME, "Payments", 0xFF059669L)
                addCategory("🏅 Bonus", CategoryType.INCOME, "TrendingUp", 0xFF16A34AL)
                addCategory("🏦interest", CategoryType.INCOME, "AccountBalance", 0xFF0D9488L)
                addCategory("💵 Petty cash", CategoryType.INCOME, "AccountBalanceWallet", 0xFF22C55EL)
                addCategory("🤑 Allowance", CategoryType.INCOME, "AddCircle", 0xFF10B981L)
                addCategory("Other", CategoryType.INCOME, "MoreHoriz", 0xFF64748BL)
            }
        }
    }

    suspend fun ensurePayLahAutoRechargeLinked(db: MoneySmartDatabase) {
        db.withTransaction {
            linkPayLahToPosbInternal(db)
        }
    }

    suspend fun applySnapshotBalances(db: MoneySmartDatabase, baseCurrency: String = "SGD"): Int {
        var updatedCount = 0
        db.withTransaction {
            val accountDao = db.accountDao()
            val allAccounts = accountDao.getAllAccounts().toMutableList()
            val activeTxs = db.transactionDao().getAllActiveTransactions()

            for (spec in snapshotAccountSpecs) {
                val existing = allAccounts.firstOrNull {
                    it.name.trim().equals(spec.name, ignoreCase = true)
                }
                if (existing == null) {
                    val insertedId = accountDao.insert(
                        AccountEntity(
                            name = spec.name,
                            type = spec.type,
                            currency = spec.currency,
                            initialBalance = spec.targetBalance,
                            iconName = spec.iconName,
                            colorHex = spec.colorHex,
                            marketSymbol = spec.defaultMarketSymbol
                        )
                    )
                    allAccounts.add(
                        AccountEntity(
                            id = insertedId,
                            name = spec.name,
                            type = spec.type,
                            currency = spec.currency,
                            initialBalance = spec.targetBalance,
                            iconName = spec.iconName,
                            colorHex = spec.colorHex,
                            marketSymbol = spec.defaultMarketSymbol
                        )
                    )
                } else {
                    val calibratedInitial = LedgerCalculations.computeCalibratedInitialBalance(
                        account = existing.copy(currency = spec.currency),
                        transactions = activeTxs,
                        targetCurrentBalance = spec.targetBalance,
                        baseCurrency = baseCurrency
                    )
                    val newMarketSymbol = existing.marketSymbol ?: spec.defaultMarketSymbol
                    val updated = existing.copy(
                        type = spec.type,
                        currency = spec.currency,
                        initialBalance = calibratedInitial,
                        isArchived = false,
                        marketSymbol = newMarketSymbol,
                        lastSyncedMarketValue = if ((existing.holdingUnits ?: 0.0) > 0.0) {
                            spec.targetBalance
                        } else {
                            existing.lastSyncedMarketValue
                        }
                    )
                    accountDao.update(updated)
                }
                updatedCount++
            }
            linkPayLahToPosbInternal(db)
        }
        return updatedCount
    }

    private suspend fun linkPayLahToPosbInternal(db: MoneySmartDatabase) {
        val accountDao = db.accountDao()
        val accounts = accountDao.getAllAccounts()
        val posb = accounts.firstOrNull { it.name.trim().equals("POSB", ignoreCase = true) } ?: return
        val payLah = accounts.firstOrNull { it.name.trim().equals("PayLah", ignoreCase = true) } ?: return
        if (payLah.autoRechargeSourceAccountId == null) {
            accountDao.update(
                payLah.copy(
                    autoRechargeSourceAccountId = posb.id,
                    autoRechargeMinBalance = 0.0
                )
            )
        }
        // Also ensure Mari Gold has default gold gram ticker if not set
        val mariGold = accounts.firstOrNull { it.name.trim().equals("Mari Gold", ignoreCase = true) }
        if (mariGold != null && mariGold.marketSymbol.isNullOrBlank()) {
            accountDao.update(mariGold.copy(marketSymbol = MarketPriceApiClient.GOLD_GRAM_SYMBOL))
        }
    }
}
