package com.moneysmart.app

import com.moneysmart.app.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

class FormatUtilsTest {

    @Test
    fun formatMoneyAndDatesStayConsistentUnderNonEnglishSystemLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE)

            assertEquals("SGD 22.80", FormatUtils.formatMoney(22.8, "SGD"))
            assertEquals("-SGD 102.40", FormatUtils.formatMoney(-102.4, "SGD"))
            assertEquals("SGD 0.00", FormatUtils.formatMoney(-0.0001, "SGD"))
            assertEquals("JPY 1,250", FormatUtils.formatMoney(1250.0, "JPY"))
            assertEquals("68.40", FormatUtils.formatCompactNumber(68.4))
            assertEquals("November 2026", FormatUtils.formatYearMonth(YearMonth.of(2026, 11)))

            val utc = ZoneId.of("UTC")
            val millis = YearMonth.of(2026, 11).atDay(22).atTime(14, 5).atZone(utc).toInstant().toEpochMilli()
            assertEquals("Nov 22, 2026", FormatUtils.formatDate(millis, utc))
            assertEquals("Sun, Nov 22", FormatUtils.formatDayHeader(millis, utc))
        } finally {
            Locale.setDefault(original)
        }
    }
}
