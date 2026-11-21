package com.moneysmart.app.data.local

import androidx.room.withTransaction
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.CategoryType

object DefaultSeedData {

    suspend fun seedDefaultsIfEmpty(db: MoneySmartDatabase, baseCurrency: String = "SGD") {
        db.withTransaction {
            val existingAccounts = db.accountDao().getAllAccounts()
            val existingCategories = db.categoryDao().getAllCategories()

            if (existingAccounts.isEmpty()) {
                val accounts = listOf(
                    AccountEntity(name = "OCBC", type = AccountType.BANK, currency = baseCurrency, initialBalance = 0.0, iconName = "AccountBalance", colorHex = 0xFFDC2626L),
                    AccountEntity(name = "POSB", type = AccountType.BANK, currency = baseCurrency, initialBalance = 0.0, iconName = "AccountBalance", colorHex = 0xFF2563EBL),
                    AccountEntity(name = "PayLah", type = AccountType.E_WALLET, currency = baseCurrency, initialBalance = 0.0, iconName = "Smartphone", colorHex = 0xFFEF4444L),
                    AccountEntity(name = "Maribank", type = AccountType.BANK, currency = baseCurrency, initialBalance = 0.0, iconName = "AccountBalance", colorHex = 0xFFF59E0BL),
                    AccountEntity(name = "Trust", type = AccountType.BANK, currency = baseCurrency, initialBalance = 0.0, iconName = "AccountBalance", colorHex = 0xFF0EA5E9L),
                    AccountEntity(name = "YouTrip-SGD", type = AccountType.E_WALLET, currency = baseCurrency, initialBalance = 0.0, iconName = "CreditCard", colorHex = 0xFF7C3AEDL),
                    AccountEntity(name = "Wise", type = AccountType.E_WALLET, currency = baseCurrency, initialBalance = 0.0, iconName = "AccountBalanceWallet", colorHex = 0xFF10B981L),
                    AccountEntity(name = "Notes", type = AccountType.CASH, currency = baseCurrency, initialBalance = 0.0, iconName = "Payments", colorHex = 0xFF0D6E52L),
                    AccountEntity(name = "Coins", type = AccountType.CASH, currency = baseCurrency, initialBalance = 0.0, iconName = "AccountBalanceWallet", colorHex = 0xFF14B8A6L),
                    AccountEntity(name = "Mari Invest", type = AccountType.INVESTMENT, currency = baseCurrency, initialBalance = 0.0, iconName = "TrendingUp", colorHex = 0xFF059669L),
                    AccountEntity(name = "Mari Gold", type = AccountType.INVESTMENT, currency = baseCurrency, initialBalance = 0.0, iconName = "TrendingUp", colorHex = 0xFFD97706L),
                    AccountEntity(name = "Trust investment", type = AccountType.INVESTMENT, currency = baseCurrency, initialBalance = 0.0, iconName = "TrendingUp", colorHex = 0xFF2563EBL),
                    AccountEntity(name = "CDC Voucher", type = AccountType.E_WALLET, currency = baseCurrency, initialBalance = 0.0, iconName = "CardGiftcard", colorHex = 0xFF6366F1L),
                    AccountEntity(name = "LifeSG", type = AccountType.E_WALLET, currency = baseCurrency, initialBalance = 0.0, iconName = "CardGiftcard", colorHex = 0xFF0D6E52L)
                )
                db.accountDao().insertAll(accounts)
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
}
