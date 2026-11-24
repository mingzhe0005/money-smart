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

                suspend fun addSpecs(type: CategoryType, specs: List<CategoryOrganizer.CategorySpec>) {
                    specs.forEach { spec ->
                        val parentId = categoryDao.insert(
                            CategoryEntity(
                                name = spec.name,
                                type = type,
                                parentId = null,
                                iconName = spec.iconName,
                                colorHex = spec.colorHex,
                                sortOrder = order++
                            )
                        )
                        if (type == CategoryType.EXPENSE) {
                            val subs = CategoryOrganizer.defaultExpenseSubcategories[spec.name].orEmpty()
                            subs.forEachIndexed { idx, subName ->
                                categoryDao.insert(
                                    CategoryEntity(
                                        name = subName,
                                        type = CategoryType.EXPENSE,
                                        parentId = parentId,
                                        iconName = spec.iconName,
                                        colorHex = spec.colorHex,
                                        sortOrder = idx + 1
                                    )
                                )
                            }
                        }
                    }
                }

                addSpecs(CategoryType.EXPENSE, CategoryOrganizer.defaultExpenseSpecs)
                addSpecs(CategoryType.INCOME, CategoryOrganizer.defaultIncomeSpecs)
            }
        }
    }

    suspend fun reorganizeCategories(db: MoneySmartDatabase) {
        db.withTransaction {
            val categoryDao = db.categoryDao()
            val txDao = db.transactionDao()
            val budgetDao = db.budgetDao()
            val recurringDao = db.recurringDao()
            val splitBillDao = db.splitBillDao()

            suspend fun loadUsageStats(): Pair<MutableMap<Long, Int>, MutableSet<Long>> {
                val activeCounts = mutableMapOf<Long, Int>()
                val referenced = mutableSetOf<Long>()
                for (tx in txDao.getAllTransactions()) {
                    val catId = tx.categoryId ?: continue
                    referenced.add(catId)
                    if (!tx.isDeleted) {
                        activeCounts[catId] = (activeCounts[catId] ?: 0) + 1
                    }
                }
                budgetDao.getAllBudgets().forEach { it.categoryId?.let(referenced::add) }
                recurringDao.getAllRecurring().forEach { it.categoryId?.let(referenced::add) }
                splitBillDao.getAllBills().forEach { it.categoryId?.let(referenced::add) }
                return activeCounts to referenced
            }

            suspend fun mergeGroup(
                group: List<CategoryEntity>,
                targetName: String,
                isParent: Boolean,
                activeCounts: MutableMap<Long, Int>
            ) {
                val winner = group.sortedWith(
                    compareByDescending<CategoryEntity> { activeCounts[it.id] ?: 0 }
                        .thenBy { it.id }
                ).first()

                val spec = if (isParent) CategoryOrganizer.canonicalSpec(targetName, winner.type) else null
                val updatedWinner = winner.copy(
                    name = targetName,
                    iconName = spec?.iconName ?: winner.iconName,
                    colorHex = spec?.colorHex ?: winner.colorHex
                )
                if (updatedWinner != winner) {
                    categoryDao.update(updatedWinner)
                }

                if (group.size <= 1) return

                val allBudgets = budgetDao.getAllBudgets().toMutableList()
                val winnerBudgetMonths = allBudgets
                    .filter { it.categoryId == winner.id }
                    .mapTo(mutableSetOf()) { it.yearMonth }

                for (loser in group) {
                    if (loser.id == winner.id) continue
                    txDao.reassignCategory(loser.id, winner.id)
                    recurringDao.reassignCategory(loser.id, winner.id)
                    splitBillDao.reassignCategory(loser.id, winner.id)
                    if (isParent) {
                        categoryDao.reassignParent(loser.id, winner.id)
                    }
                    for (b in allBudgets.filter { it.categoryId == loser.id }) {
                        if (b.yearMonth in winnerBudgetMonths) {
                            budgetDao.deleteById(b.id)
                        } else {
                            budgetDao.update(b.copy(categoryId = winner.id))
                            winnerBudgetMonths.add(b.yearMonth)
                        }
                    }
                    activeCounts[winner.id] = (activeCounts[winner.id] ?: 0) + (activeCounts[loser.id] ?: 0)
                    categoryDao.deleteById(loser.id)
                }
            }

            // 1. Merge & clean parent categories
            val (parentCounts, _) = loadUsageStats()
            val parentsByGroup = categoryDao.getAllCategories()
                .filter { it.parentId == null }
                .groupBy { cat ->
                    cat.type to CategoryOrganizer.canonicalName(cat.name, cat.type).lowercase()
                }
            for ((key, group) in parentsByGroup) {
                val canonical = CategoryOrganizer.canonicalName(group.first().name, key.first)
                mergeGroup(group, canonical, isParent = true, activeCounts = parentCounts)
            }

            // 1b. Demote top-level "Snacks" under "Food", ensure Food -> Snacks & Reimbursement exist
            val catsBeforeSubs = categoryDao.getAllCategories()
            val foodSpec = CategoryOrganizer.canonicalSpec("Food", CategoryType.EXPENSE)!!
            val existingFood = catsBeforeSubs.firstOrNull {
                it.type == CategoryType.EXPENSE && it.parentId == null && it.name.equals("Food", ignoreCase = true)
            }
            val foodId = existingFood?.id ?: categoryDao.insert(
                CategoryEntity(
                    name = foodSpec.name,
                    type = CategoryType.EXPENSE,
                    parentId = null,
                    iconName = foodSpec.iconName,
                    colorHex = foodSpec.colorHex,
                    sortOrder = 1
                )
            )
            val topLevelSnacks = catsBeforeSubs.firstOrNull {
                it.type == CategoryType.EXPENSE && it.parentId == null && it.name.equals("Snacks", ignoreCase = true)
            }
            if (topLevelSnacks != null) {
                categoryDao.reassignParent(topLevelSnacks.id, foodId)
                categoryDao.update(
                    topLevelSnacks.copy(
                        parentId = foodId,
                        name = "Snacks",
                        iconName = foodSpec.iconName,
                        colorHex = foodSpec.colorHex
                    )
                )
            } else {
                val hasSnacksSub = catsBeforeSubs.any {
                    it.type == CategoryType.EXPENSE && it.parentId == foodId && it.name.equals("Snacks", ignoreCase = true)
                }
                if (!hasSnacksSub) {
                    categoryDao.insert(
                        CategoryEntity(
                            name = "Snacks",
                            type = CategoryType.EXPENSE,
                            parentId = foodId,
                            iconName = foodSpec.iconName,
                            colorHex = foodSpec.colorHex,
                            sortOrder = 2
                        )
                    )
                }
            }
            val hasReimbursement = catsBeforeSubs.any {
                it.type == CategoryType.INCOME &&
                    it.parentId == null &&
                    CategoryOrganizer.isReimbursementCategoryName(it.name)
            }
            if (!hasReimbursement) {
                val reimbSpec = CategoryOrganizer.canonicalSpec("Reimbursement", CategoryType.INCOME)!!
                categoryDao.insert(
                    CategoryEntity(
                        name = reimbSpec.name,
                        type = CategoryType.INCOME,
                        parentId = null,
                        iconName = reimbSpec.iconName,
                        colorHex = reimbSpec.colorHex,
                        sortOrder = 6
                    )
                )
            }

            // 2. Merge & clean subcategories within each parent
            val (childCounts, _) = loadUsageStats()
            val childrenByGroup = categoryDao.getAllCategories()
                .filter { it.parentId != null }
                .groupBy { child ->
                    Triple(
                        child.type,
                        child.parentId!!,
                        CategoryOrganizer.cleanSubcategoryName(child.name).lowercase()
                    )
                }
            for ((_, group) in childrenByGroup) {
                val cleanSub = CategoryOrganizer.cleanSubcategoryName(group.first().name)
                mergeGroup(group, cleanSub, isParent = false, activeCounts = childCounts)
            }

            // 3. Prune unused legacy seed categories (e.g., empty Shopping / Bills & Utilities / Entertainment)
            val (_, referencedIds) = loadUsageStats()
            val catsAfterMerge = categoryDao.getAllCategories()
            val parentsWithChildren = catsAfterMerge.mapNotNull { it.parentId }.toSet()
            for (cat in catsAfterMerge) {
                if (cat.parentId == null &&
                    cat.id !in referencedIds &&
                    cat.id !in parentsWithChildren &&
                    cat.name.trim().lowercase() in CategoryOrganizer.legacyUnusedCategoryNames
                ) {
                    categoryDao.deleteById(cat.id)
                }
            }

            // 4. Normalize sortOrder per type (canonical order first, custom before Other, Other last)
            val finalCats = categoryDao.getAllCategories()
            for (type in CategoryType.entries) {
                val orderedParents = finalCats
                    .filter { it.type == type && it.parentId == null }
                    .sortedWith(
                        compareBy<CategoryEntity> { cat ->
                            val isOther = cat.name.equals("Other", ignoreCase = true)
                            when {
                                isOther -> Int.MAX_VALUE
                                else -> CategoryOrganizer.canonicalSortRank(cat.name, type) ?: 10_000
                            }
                        }.thenBy { it.name.lowercase() }
                    )
                orderedParents.forEachIndexed { idx, parent ->
                    val targetOrder = idx + 1
                    if (parent.sortOrder != targetOrder) {
                        categoryDao.update(parent.copy(sortOrder = targetOrder))
                    }
                    val orderedChildren = finalCats
                        .filter { it.type == type && it.parentId == parent.id }
                        .sortedBy { it.name.lowercase() }
                    orderedChildren.forEachIndexed { cIdx, child ->
                        val childOrder = cIdx + 1
                        if (child.sortOrder != childOrder) {
                            categoryDao.update(child.copy(sortOrder = childOrder))
                        }
                    }
                }
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
