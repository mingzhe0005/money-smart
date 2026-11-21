package com.moneysmart.app.util

import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

object FormatUtils {
    val supportedCurrencies = listOf(
        "USD", "EUR", "GBP", "SGD", "MYR", "JPY", "AUD", "CAD",
        "CNY", "HKD", "TWD", "KRW", "INR", "IDR", "THB", "PHP",
        "VND", "CHF", "NZD", "AED"
    )

    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.getDefault())
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val dayHeaderFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val isoDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun formatMoney(amount: Double, currencyCode: String): String {
        return try {
            val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
            val currency = Currency.getInstance(currencyCode.uppercase())
            format.currency = currency
            format.maximumFractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
            format.format(amount)
        } catch (_: Exception) {
            String.format(Locale.getDefault(), "%s %.2f", currencyCode.uppercase(), amount)
        }
    }

    fun formatCompactNumber(amount: Double): String =
        String.format(Locale.getDefault(), "%.2f", amount)

    fun formatDate(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zoneId).format(dateFormatter)

    fun formatDateTime(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zoneId).format(dateTimeFormatter)

    fun formatTime(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zoneId).format(timeFormatter)

    fun formatDayHeader(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zoneId).format(dayHeaderFormatter)

    fun formatIsoDateTime(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zoneId).format(isoDateTimeFormatter)

    fun formatYearMonth(yearMonth: YearMonth): String =
        yearMonth.format(monthYearFormatter)

    fun yearMonthKey(yearMonth: YearMonth): String = yearMonth.toString() // YYYY-MM

    fun yearMonthKeyFromMillis(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val localDate = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
        return YearMonth.from(localDate).toString()
    }

    fun monthRangeMillis(yearMonth: YearMonth, zoneId: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val start = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return start to end
    }

    fun yearRangeMillis(year: Int, zoneId: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val start = YearMonth.of(year, 1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = YearMonth.of(year + 1, 1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return start to end
    }
}
