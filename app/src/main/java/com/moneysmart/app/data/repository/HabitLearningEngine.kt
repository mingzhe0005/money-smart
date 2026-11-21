package com.moneysmart.app.data.repository

import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionType

data class LearnedHabit(
    val keyword: String,
    val type: TransactionType,
    val categoryId: Long?,
    val categoryName: String,
    val accountId: Long,
    val accountName: String,
    val occurrences: Int
)

object HabitLearningEngine {

    private val ignoreKeywords = setOf(
        "payment", "purchase", "paid", "transfer", "topup", "top-up",
        "debit", "credit", "card", "cash", "expense", "income", "other"
    )

    fun extractTopHabits(
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): List<LearnedHabit> {
        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }

        data class PatternKey(
            val type: TransactionType,
            val categoryId: Long?,
            val accountId: Long
        )

        val grouped = mutableMapOf<String, MutableMap<PatternKey, Int>>()

        for (tx in transactions) {
            if (tx.isDeleted || tx.isReimbursement) continue
            if (tx.type != TransactionType.EXPENSE && tx.type != TransactionType.INCOME) continue
            val rawLabel = tx.merchant.ifBlank { tx.note }.trim()
            val normalized = normalizeKeyword(rawLabel) ?: continue

            val key = PatternKey(
                type = tx.type,
                categoryId = tx.categoryId,
                accountId = tx.accountId
            )
            val counts = grouped.getOrPut(normalized) { mutableMapOf() }
            counts[key] = (counts[key] ?: 0) + 1
        }

        return grouped.mapNotNull { (keyword, counts) ->
            val best = counts.maxByOrNull { it.value } ?: return@mapNotNull null
            val totalOccurrences = counts.values.sum()
            val accName = accountMap[best.key.accountId]?.name ?: return@mapNotNull null
            val catName = best.key.categoryId?.let { categoryMap[it]?.name } ?: "Uncategorized"
            LearnedHabit(
                keyword = keyword,
                type = best.key.type,
                categoryId = best.key.categoryId,
                categoryName = catName,
                accountId = best.key.accountId,
                accountName = accName,
                occurrences = totalOccurrences
            )
        }.sortedByDescending { it.occurrences }
    }

    fun selectRelevantHabitsForNotification(
        habits: List<LearnedHabit>,
        appName: String,
        title: String,
        text: String,
        maxDirectMatches: Int = 10,
        maxGeneralTop: Int = 15
    ): List<LearnedHabit> {
        if (habits.isEmpty()) return emptyList()

        val combinedLower = "$appName $title $text".lowercase()
        val notifTokens = combinedLower
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .toSet()

        val directMatches = habits.filter { habit ->
            if (habit.keyword.length >= 3 && combinedLower.contains(habit.keyword)) {
                true
            } else {
                val habitTokens = habit.keyword.split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
                habitTokens.any { it in notifTokens }
            }
        }.take(maxDirectMatches)

        val directKeywords = directMatches.mapTo(mutableSetOf()) { it.keyword }
        val generalTop = habits
            .filter { it.keyword !in directKeywords && it.occurrences >= 2 }
            .take(maxGeneralTop)

        return directMatches + generalTop
    }

    fun buildHabitPromptSection(habits: List<LearnedHabit>): String {
        if (habits.isEmpty()) return ""
        return habits.joinToString("\n") { habit ->
            val catPart = if (habit.categoryId != null) {
                "Category=\"${habit.categoryName}\" (id=${habit.categoryId})"
            } else {
                "Category=null"
            }
            "- \"${habit.keyword}\" -> ${habit.type.name}, $catPart, Account=\"${habit.accountName}\" (id=${habit.accountId}) [${habit.occurrences}x]"
        }
    }

    internal fun normalizeKeyword(raw: String): String? {
        val cleaned = raw
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length < 2) return null
        if (cleaned.all { it.isDigit() || it == '.' || it == '-' }) return null
        if (cleaned in ignoreKeywords) return null
        return cleaned.take(40)
    }
}
