package com.moneysmart.app.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

object FormatUtils {
    val supportedCurrencies = listOf(
        "USD", "EUR", "GBP", "SGD", "MYR", "JPY", "AUD", "CAD",
        "CNY", "HKD", "TWD", "KRW", "INR", "IDR", "THB", "PHP",
        "VND", "CHF", "NZD", "AED"
    )

    private val displayLocale: Locale = Locale.ENGLISH
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", displayLocale)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", displayLocale)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", displayLocale)
    private val dayHeaderFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", displayLocale)
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", displayLocale)
    private val shortMonthFormatter = DateTimeFormatter.ofPattern("MMM", displayLocale)
    private val isoDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun formatMoney(amount: Double, currencyCode: String): String {
        val code = currencyCode.uppercase(Locale.US)
        val fractionDigits = runCatching {
            Currency.getInstance(code).defaultFractionDigits.coerceAtLeast(0)
        }.getOrDefault(2)
        val normalized = if (abs(amount) < 0.0005) 0.0 else amount
        val sign = if (normalized < 0.0) "-" else ""
        val pattern = if (fractionDigits > 0) {
            "#,##0.${"0".repeat(fractionDigits)}"
        } else {
            "#,##0"
        }
        val df = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
        return "$sign$code ${df.format(abs(normalized))}"
    }

    fun formatCompactNumber(amount: Double): String =
        String.format(Locale.US, "%.2f", amount)

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
