package com.moneysmart.app

import com.moneysmart.app.data.local.CategoryOrganizer
import com.moneysmart.app.data.local.entity.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryOrganizerTest {

    @Test
    fun stripsEmojisAndNormalizesKnownExpenseAndIncomeCategories() {
        assertEquals("Food", CategoryOrganizer.canonicalName("🍜 Food", CategoryType.EXPENSE))
        assertEquals("Snacks", CategoryOrganizer.canonicalName("🍿Snacks", CategoryType.EXPENSE))
        assertEquals("With Lulu", CategoryOrganizer.canonicalName("with lulu ❤️", CategoryType.EXPENSE))
        assertEquals("Accommodation", CategoryOrganizer.canonicalName("🛌 accomodation", CategoryType.EXPENSE))
        assertEquals("Accommodation", CategoryOrganizer.canonicalName("🛏 accomodation", CategoryType.EXPENSE))
        assertEquals("Social Life", CategoryOrganizer.canonicalName("👬🏻 Social Life", CategoryType.EXPENSE))
        assertEquals("Interest", CategoryOrganizer.canonicalName("🏦interest", CategoryType.INCOME))
        assertEquals("Petty Cash", CategoryOrganizer.canonicalName("💵 Petty cash", CategoryType.INCOME))
    }

    @Test
    fun normalizesCommonAliasesAcrossDefaultAndExportCategories() {
        assertEquals("Food", CategoryOrganizer.canonicalName("Food & Dining", CategoryType.EXPENSE))
        assertEquals("Transport", CategoryOrganizer.canonicalName("Transportation", CategoryType.EXPENSE))
        assertEquals("Other", CategoryOrganizer.canonicalName("Others", CategoryType.EXPENSE))
        assertEquals("Other", CategoryOrganizer.canonicalName("Other Income", CategoryType.INCOME))
        assertEquals("Other", CategoryOrganizer.canonicalName("💰", CategoryType.INCOME))
    }

    @Test
    fun preservesCustomCategoriesWithoutEmojis() {
        assertEquals("Japan Trip", CategoryOrganizer.canonicalName("✈️ Japan Trip", CategoryType.EXPENSE))
        assertEquals("Bubble tea", CategoryOrganizer.cleanSubcategoryName("🧋 bubble tea"))
    }
}
