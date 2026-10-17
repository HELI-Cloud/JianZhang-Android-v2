package com.jianzhang.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class DatesTest {
    @Test
    fun `monthly rule keeps the original day anchor across short months`() {
        val anchor = LocalDate.of(2026, 1, 31)

        assertThat(Dates.occurrenceAt(anchor, 1, "MONTHLY", 1))
            .isEqualTo(LocalDate.of(2026, 2, 28))
        assertThat(Dates.occurrenceAt(anchor, 2, "MONTHLY", 1))
            .isEqualTo(LocalDate.of(2026, 3, 31))
        assertThat(Dates.nextAfter(anchor, LocalDate.of(2026, 2, 28), "MONTHLY", 1))
            .isEqualTo(LocalDate.of(2026, 3, 31))
    }

    @Test
    fun `legacy lowercase frequency is treated as its intended cadence`() {
        val anchor = LocalDate.of(2026, 1, 31)

        assertThat(Dates.nextAfter(anchor, LocalDate.of(2026, 2, 28), "monthly", 1))
            .isEqualTo(LocalDate.of(2026, 3, 31))
        assertThat(Dates.nextAfter(anchor, LocalDate.of(2026, 1, 31), " weekly ", 1))
            .isEqualTo(LocalDate.of(2026, 2, 7))
    }

    @Test
    fun `yearly leap day stays anchored`() {
        val anchor = LocalDate.of(2024, 2, 29)

        assertThat(Dates.occurrenceAt(anchor, 1, "YEARLY", 1))
            .isEqualTo(LocalDate.of(2025, 2, 28))
        assertThat(Dates.occurrenceAt(anchor, 4, "YEARLY", 1))
            .isEqualTo(LocalDate.of(2028, 2, 29))
    }

    @Test
    fun `interval is applied from the original anchor`() {
        val anchor = LocalDate.of(2026, 1, 5)

        assertThat(Dates.occurrenceAt(anchor, 3, "WEEKLY", 2))
            .isEqualTo(LocalDate.of(2026, 2, 16))
        assertThat(Dates.firstOccurrenceOnOrAfter(anchor, LocalDate.of(2026, 2, 17), "WEEKLY", 2))
            .isEqualTo(LocalDate.of(2026, 3, 2))
    }

    @Test
    fun `nextAfter is strictly after the current occurrence`() {
        val anchor = LocalDate.of(2026, 1, 1)

        assertThat(Dates.nextAfter(anchor, anchor, "WEEKLY", 1))
            .isEqualTo(LocalDate.of(2026, 1, 8))
    }

    @Test
    fun `invalid dates are rejected instead of silently becoming today`() {
        assertThat(Dates.parseOrNull("2026-02-30")).isNull()
        assertThat(Dates.parseMonthOrNull("2026-13")).isNull()
        val dateFailure = runCatching { Dates.parse("2026-02-30") }
        val monthFailure = runCatching { Dates.parseMonth("2026-13") }
        assertThat(dateFailure.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(monthFailure.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }
    @Test
    fun `invalid frequency falls back to daily`() {
        val anchor = LocalDate.of(2026, 1, 1)

        assertThat(Dates.occurrenceAt(anchor, 2, "not-a-frequency", 1))
            .isEqualTo(LocalDate.of(2026, 1, 3))
        assertThat(Dates.normalizeFrequency("monthly")).isEqualTo("MONTHLY")
    }
}
