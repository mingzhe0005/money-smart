package com.moneysmart.app.data.local

import com.moneysmart.app.data.local.entity.CategoryType

object CategoryOrganizer {

    data class CategorySpec(
        val name: String,
        val iconName: String,
        val colorHex: Long
    )

    val defaultExpenseSpecs: List<CategorySpec> = listOf(
        CategorySpec("Food", "Restaurant", 0xFFEF4444L),
        CategorySpec("Transport", "DirectionsCar", 0xFF0EA5E9L),
        CategorySpec("With Lulu", "CardGiftcard", 0xFFF43F5EL),
        CategorySpec("Social Life", "ShoppingBag", 0xFFEC4899L),
        CategorySpec("Apparel", "ShoppingBag", 0xFFA855F7L),
        CategorySpec("Accommodation", "Home", 0xFF6366F1L),
        CategorySpec("Household", "Home", 0xFF84CC16L),
        CategorySpec("Health", "LocalHospital", 0xFF10B981L),
        CategorySpec("Culture", "Movie", 0xFF14B8A6L),
        CategorySpec("Education", "School", 0xFF3B82F6L),
        CategorySpec("Gift", "CardGiftcard", 0xFF8B5CF6L),
        CategorySpec("Other", "MoreHoriz", 0xFF64748BL)
    )

    val defaultExpenseSubcategories: Map<String, List<String>> = mapOf(
        "Food" to listOf("Eating Out", "Snacks", "Coffee & Drinks", "Groceries"),
        "Transport" to listOf("Public Transit", "Ride-Hailing", "Fuel & Parking")
    )

    val defaultIncomeSpecs: List<CategorySpec> = listOf(
        CategorySpec("Salary", "Payments", 0xFF059669L),
        CategorySpec("Bonus", "TrendingUp", 0xFF16A34AL),
        CategorySpec("Allowance", "AddCircle", 0xFF10B981L),
        CategorySpec("Interest", "AccountBalance", 0xFF0D9488L),
        CategorySpec("Petty Cash", "AccountBalanceWallet", 0xFF22C55EL),
        CategorySpec("Reimbursement", "SwapHoriz", 0xFF0EA5E9L),
        CategorySpec("Other", "MoreHoriz", 0xFF64748BL)
    )

    val legacyUnusedCategoryNames: Set<String> = setOf(
        "shopping",
        "bills & utilities",
        "utilities",
        "groceries",
        "entertainment",
        "subscriptions",
        "travel",
        "freelance",
        "investments",
        "investment"
    )

    private val expenseAliases: Map<String, String> = mapOf(
        "food" to "Food",
        "food & dining" to "Food",
        "dining" to "Food",
        "snacks" to "Snacks",
        "snack" to "Snacks",
        "transport" to "Transport",
        "transportation" to "Transport",
        "with lulu" to "With Lulu",
        "social life" to "Social Life",
        "social" to "Social Life",
        "apparel" to "Apparel",
        "clothing" to "Apparel",
        "accomodation" to "Accommodation",
        "accommodation" to "Accommodation",
        "housing" to "Accommodation",
        "household" to "Household",
        "health" to "Health",
        "healthcare" to "Health",
        "culture" to "Culture",
        "education" to "Education",
        "gift" to "Gift",
        "gifts" to "Gift",
        "other" to "Other",
        "others" to "Other",
        "other expense" to "Other"
    )

    private val incomeAliases: Map<String, String> = mapOf(
        "salary" to "Salary",
        "bonus" to "Bonus",
        "allowance" to "Allowance",
        "interest" to "Interest",
        "petty cash" to "Petty Cash",
        "reimbursement" to "Reimbursement",
        "reimbursements" to "Reimbursement",
        "cashback" to "Reimbursement",
        "rebate" to "Reimbursement",
        "refund" to "Reimbursement",
        "other" to "Other",
        "others" to "Other",
        "other income" to "Other"
    )

    private val specsByTypeAndKey: Map<Pair<CategoryType, String>, CategorySpec> = buildMap {
        defaultExpenseSpecs.forEach { put(CategoryType.EXPENSE to it.name.lowercase(), it) }
        defaultIncomeSpecs.forEach { put(CategoryType.INCOME to it.name.lowercase(), it) }
    }

    private val orderByTypeAndKey: Map<Pair<CategoryType, String>, Int> = buildMap {
        defaultExpenseSpecs.forEachIndexed { idx, spec ->
            put(CategoryType.EXPENSE to spec.name.lowercase(), idx + 1)
        }
        defaultIncomeSpecs.forEachIndexed { idx, spec ->
            put(CategoryType.INCOME to spec.name.lowercase(), idx + 1)
        }
    }

    fun stripEmojis(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            val charCount = Character.charCount(cp)
            val type = Character.getType(cp)
            val isEmojiOrModifier = type == Character.OTHER_SYMBOL.toInt() ||
                type == Character.MODIFIER_SYMBOL.toInt() ||
                type == Character.FORMAT.toInt() ||
                cp == 0xFE0E ||
                cp == 0xFE0F ||
                cp == 0x20E3
            if (!isEmojiOrModifier) {
                sb.appendCodePoint(cp)
            }
            i += charCount
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    fun cleanSubcategoryName(raw: String): String {
        val cleaned = stripEmojis(raw)
        if (cleaned.isBlank()) return "Other"
        return if (cleaned == cleaned.lowercase()) {
            cleaned.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }
        } else {
            cleaned
        }
    }

    fun canonicalName(raw: String, type: CategoryType): String {
        val cleaned = stripEmojis(raw)
        if (cleaned.isBlank()) return "Other"

        val lower = cleaned.lowercase()
        val aliases = if (type == CategoryType.EXPENSE) expenseAliases else incomeAliases
        aliases[lower]?.let { return it }

        return if (cleaned == lower) {
            cleaned.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }
        } else {
            cleaned
        }
    }

    fun canonicalSpec(name: String, type: CategoryType): CategorySpec? {
        return specsByTypeAndKey[type to canonicalName(name, type).lowercase()]
    }

    fun canonicalSortRank(name: String, type: CategoryType): Int? {
        return orderByTypeAndKey[type to name.trim().lowercase()]
    }

    fun isReimbursementCategoryName(name: String?): Boolean {
        return name?.trim()?.equals("Reimbursement", ignoreCase = true) == true
    }
}
