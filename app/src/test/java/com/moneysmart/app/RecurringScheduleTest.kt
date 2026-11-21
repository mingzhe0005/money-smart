package com.moneysmart.app

import com.moneysmart.app.data.local.entity.RecurrenceFrequency
import com.moneysmart.app.data.repository.LedgerCalculations
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurringScheduleTest {

    @Test
    fun recurringFrequenciesAdvanceDatesAccurately() {
        val zone = ZoneId.of("UTC")
        val jan31 = ZonedDateTime.of(2026, 1, 31, 9, 0, 0, 0, zone).toInstant().toEpochMilli()

        val nextMonth = LedgerCalculations.computeNextDueMillis(
            currentDueMillis = jan31,
            frequency = RecurrenceFrequency.MONTHLY,
            intervalCount = 1,
            zoneId = zone
        )
        val nextMonthDate = Instant.ofEpochMilli(nextMonth).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 2, 28), nextMonthDate)

        val nextTwoWeeks = LedgerCalculations.computeNextDueMillis(
            currentDueMillis = jan31,
            frequency = RecurrenceFrequency.WEEKLY,
            intervalCount = 2,
            zoneId = zone
        )
        val nextTwoWeeksDate = Instant.ofEpochMilli(nextTwoWeeks).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 2, 14), nextTwoWeeksDate)

        val nextYear = LedgerCalculations.computeNextDueMillis(
            currentDueMillis = jan31,
            frequency = RecurrenceFrequency.YEARLY,
            intervalCount = 1,
            zoneId = zone
        )
        val nextYearDate = Instant.ofEpochMilli(nextYear).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2027, 1, 31), nextYearDate)
    }
}
