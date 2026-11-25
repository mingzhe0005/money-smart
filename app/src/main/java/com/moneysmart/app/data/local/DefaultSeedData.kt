package com.moneysmart.app.data.local

import androidx.room.withTransaction
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.CategoryType

object DefaultSeedData {

    data class StarterAccountTemplate(
        val id: String,
        val name: String,
        val type: AccountType,
        val iconName: String,
        val colorHex: Long,
        val defaultSelected: Boolean = false
    )

    val starterAccountTemplates: List<StarterAccountTemplate> = listOf(
        StarterAccountTemplate("cash", "Cash", AccountType.CASH, "AccountBalanceWallet", 0xFF14B8A6L, defaultSelected = true),
        StarterAccountTemplate("dbs_posb", "DBS / POSB", AccountType.BANK, "AccountBalance", 0xFF2563EBL, defaultSelected = true),
        StarterAccountTemplate("ocbc", "OCBC", AccountType.BANK, "AccountBalance", 0xFFDC2626L),
        StarterAccountTemplate("uob", "UOB", AccountType.BANK, "AccountBalance", 0xFF1D4ED8L),
        StarterAccountTemplate("maribank", "MariBank", AccountType.BANK, "AccountBalance", 0xFFF59E0BL),
        StarterAccountTemplate("trust", "Trust", AccountType.BANK, "AccountBalance", 0xFF0EA5E9L),
        StarterAccountTemplate("paylah", "PayLah!", AccountType.E_WALLET, "Smartphone", 0xFFEF4444L),
        StarterAccountTemplate("youtrip", "YouTrip", AccountType.E_WALLET, "CreditCard", 0xFF7C3AEDL),
        StarterAccountTemplate("wise", "Wise", AccountType.E_WALLET, "AccountBalanceWallet", 0xFF10B981L),
        StarterAccountTemplate("grabpay", "GrabPay", AccountType.E_WALLET, "Smartphone", 0xFF16A34AL),
        StarterAccountTemplate("main_bank", "Main Bank", AccountType.BANK, "AccountBalance", 0xFF0D6E52L)
    )

    suspend fun seedDefaultsIfEmpty(db: MoneySmartDatabase, baseCurrency: String = "SGD") {
        db.withTransaction {
            val existingCategories = db.categoryDao().getAllCategories()

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

    private suspend fun linkPayLahToPosbInternal(db: MoneySmartDatabase) {
        val accountDao = db.accountDao()
        val accounts = accountDao.getAllAccounts()
        val posb = accounts.firstOrNull {
            val n = it.name.trim()
            n.equals("POSB", ignoreCase = true) ||
                n.equals("DBS / POSB", ignoreCase = true) ||
                n.equals("DBS", ignoreCase = true)
        } ?: return
        val payLah = accounts.firstOrNull {
            val n = it.name.trim()
            n.equals("PayLah", ignoreCase = true) || n.equals("PayLah!", ignoreCase = true)
        } ?: return
        if (payLah.autoRechargeSourceAccountId == null) {
            accountDao.update(
                payLah.copy(
                    autoRechargeSourceAccountId = posb.id,
                    autoRechargeMinBalance = 0.0
                )
            )
        }
    }
}
