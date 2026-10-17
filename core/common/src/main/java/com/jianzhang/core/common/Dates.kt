package com.jianzhang.core.common

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object Dates {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE
    private val cnDate = DateTimeFormatter.ofPattern("yyyy年M月d日")
    private val cnMonth = DateTimeFormatter.ofPattern("yyyy年M月")
    private val weekdayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun today(): LocalDate = LocalDate.now()

    fun parseOrNull(value: String): LocalDate? =
        runCatching { LocalDate.parse(value, iso) }.getOrNull()

    fun parse(value: String): LocalDate = parseOrNull(value)
        ?: throw IllegalArgumentException("日期格式不正确：$value")

    fun iso(date: LocalDate): String = date.format(iso)

    fun monthKey(date: LocalDate): String = YearMonth.from(date).toString()

    fun parseMonthOrNull(value: String): YearMonth? =
        runCatching { YearMonth.parse(value) }.getOrNull()

    fun parseMonth(value: String): YearMonth = parseMonthOrNull(value)
        ?: throw IllegalArgumentException("月份格式不正确：$value")

    fun monthRange(month: YearMonth): Pair<String, String> {
        val start = month.atDay(1)
        val end = start.plusMonths(1)
        return iso(start) to iso(end)
    }

    fun dateRange(start: LocalDate, endInclusive: LocalDate): Pair<String, String> =
        iso(start) to iso(endInclusive.plusDays(1))

    fun lastNDays(days: Int, today: LocalDate = today()): Pair<String, String> =
        dateRange(today.minusDays((days - 1).toLong()), today)

    fun lastNMonths(months: Int, today: LocalDate = today()): Pair<String, String> {
        val start = YearMonth.from(today).minusMonths((months - 1).toLong()).atDay(1)
        return start.toString() to today.plusDays(1).toString()
    }

    fun thisYear(today: LocalDate = today()): Pair<String, String> =
        today.withDayOfYear(1).toString() to today.plusDays(1).toString()

    fun cnDate(date: LocalDate): String = date.format(cnDate)

    fun cnMonth(month: YearMonth): String = month.format(cnMonth)

    fun weekday(date: LocalDate): String = weekdayNames[date.dayOfWeek.value - 1]

    fun daysLeftInMonth(today: LocalDate = today()): Int =
        today.lengthOfMonth() - today.dayOfMonth

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** The nth occurrence counted from the original anchor. Index 0 is the anchor itself. */
    fun occurrenceAt(
        anchor: LocalDate,
        index: Int,
        frequency: String,
        interval: Int,
    ): LocalDate {
        val step = interval.coerceAtLeast(1).toLong()
        val n = index.coerceAtLeast(0).toLong()
        return when (normalizeFrequency(frequency)) {
            "WEEKLY" -> anchor.plusWeeks(n * step)
            "MONTHLY" -> {
                val month = YearMonth.from(anchor).plusMonths(n * step)
                month.atDay(anchor.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
            }
            "YEARLY" -> {
                val year = anchor.year + (n * step).toInt()
                val month = YearMonth.of(year, anchor.monthValue)
                month.atDay(anchor.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
            }
            else -> anchor.plusDays(n * step)
        }
    }

    /** First occurrence on or after [target], always expanded from the original [anchor]. */
    fun firstOccurrenceOnOrAfter(
        anchor: LocalDate,
        target: LocalDate,
        frequency: String,
        interval: Int,
    ): LocalDate {
        if (!anchor.isBefore(target)) return anchor
        val step = interval.coerceAtLeast(1).toLong()
        val kind = normalizeFrequency(frequency)
        val estimate = when (kind) {
            "WEEKLY" -> ceilDiv(java.time.temporal.ChronoUnit.WEEKS.between(anchor, target), step)
            "MONTHLY" -> ceilDiv(java.time.temporal.ChronoUnit.MONTHS.between(anchor, target), step)
            "YEARLY" -> ceilDiv(java.time.temporal.ChronoUnit.YEARS.between(anchor, target), step)
            else -> ceilDiv(java.time.temporal.ChronoUnit.DAYS.between(anchor, target), step)
        }.coerceAtLeast(0L)
        var index = estimate
        var candidate = occurrenceAt(anchor, index.toInt(), kind, interval)
        var guard = 0
        while (candidate.isBefore(target) && guard++ < 10_000) {
            index += 1
            candidate = occurrenceAt(anchor, index.toInt(), kind, interval)
        }
        return candidate
    }

    /** First occurrence strictly after [after], expanded from the original [anchor]. */
    fun nextAfter(
        anchor: LocalDate,
        after: LocalDate,
        frequency: String,
        interval: Int,
    ): LocalDate = firstOccurrenceOnOrAfter(anchor, after.plusDays(1), frequency, interval)

    /**
     * Legacy rows may store lowercase names ("monthly"), while new rows use enum names.
     * Normalize at the scheduling boundary so imported rules keep their intended cadence.
     */
    fun normalizeFrequency(value: String): String = when (value.trim().uppercase()) {
        "WEEKLY" -> "WEEKLY"
        "MONTHLY" -> "MONTHLY"
        "YEARLY" -> "YEARLY"
        else -> "DAILY"
    }

    private fun ceilDiv(value: Long, divisor: Long): Long {
        if (value <= 0L) return 0L
        return (value + divisor - 1L) / divisor
    }
}
