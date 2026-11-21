package com.moneysmart.app

import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.CategoryType
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.data.repository.HabitLearningEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitLearningTest {

    @Test
    fun extractsFrequentMerchantHabitsAndMatchesIncomingNotification() {
        val accounts = listOf(
            AccountEntity(id = 1L, name = "OCBC", type = AccountType.BANK, currency = "SGD"),
            AccountEntity(id = 2L, name = "PayLah", type = AccountType.E_WALLET, currency = "SGD")
        )
        val categories = listOf(
            CategoryEntity(id = 10L, name = "🍿Snacks", type = CategoryType.EXPENSE),
            CategoryEntity(id = 11L, name = "🍜 Food", type = CategoryType.EXPENSE)
        )

        val history = listOf(
            TransactionEntity(id = 1, type = TransactionType.EXPENSE, amount = 3.58, currency = "SGD", baseAmount = 3.58, accountId = 1L, categoryId = 10L, merchant = "luckin coffee"),
            TransactionEntity(id = 2, type = TransactionType.EXPENSE, amount = 4.20, currency = "SGD", baseAmount = 4.20, accountId = 1L, categoryId = 10L, merchant = "Luckin Coffee"),
            TransactionEntity(id = 3, type = TransactionType.EXPENSE, amount = 6.60, currency = "SGD", baseAmount = 6.60, accountId = 2L, categoryId = 11L, merchant = "koufu"),
            TransactionEntity(id = 4, type = TransactionType.EXPENSE, amount = 7.20, currency = "SGD", baseAmount = 7.20, accountId = 2L, categoryId = 11L, merchant = "Koufu")
        )

        val habits = HabitLearningEngine.extractTopHabits(history, accounts, categories)
        assertEquals(2, habits.size)

        val selected = HabitLearningEngine.selectRelevantHabitsForNotification(
            habits = habits,
            appName = "OCBC Digital",
            title = "Card Payment",
            text = "SGD 3.90 paid at LUCKIN COFFEE SINGAPORE"
        )
        assertTrue(selected.isNotEmpty())
        assertEquals("luckin coffee", selected.first().keyword)
        assertEquals(10L, selected.first().categoryId)

        val promptSection = HabitLearningEngine.buildHabitPromptSection(selected)
        assertTrue(promptSection.contains("luckin coffee"))
        assertTrue(promptSection.contains("🍿Snacks"))
        assertTrue(promptSection.contains("OCBC"))
    }
}
