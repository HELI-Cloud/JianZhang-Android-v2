package com.jianzhang.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTest {
    @Test
    fun `parse accepts currency symbols grouping and decimal places`() {
        assertThat(Money.parseToCents("1,234.56")).isEqualTo(123456L)
        assertThat(Money.parseToCents("¥12")).isEqualTo(1200L)
        assertThat(Money.parseToCents("￥0.01")).isEqualTo(1L)
        assertThat(Money.parseToCents("")).isNull()
        assertThat(Money.parseToCents("abc")).isNull()
    }

    @Test
    fun `parse rounds half up to cents`() {
        assertThat(Money.parseToCents("10.005")).isEqualTo(1001L)
        assertThat(Money.parseToCents("-10.005")).isEqualTo(-1001L)
    }

    @Test
    fun `format handles signs and large amounts`() {
        assertThat(Money.format(123456L)).isEqualTo("1,234.56")
        assertThat(Money.format(123456L, showSign = true)).isEqualTo("+1,234.56")
        assertThat(Money.format(-123456L, showSign = true)).isEqualTo("-1,234.56")
        assertThat(Money.format(0L)).isEqualTo("0.00")
    }

    @Test
    fun `splitForDisplay uses the absolute value`() {
        assertThat(Money.splitForDisplay(-123456L)).isEqualTo("1,234" to "56")
        assertThat(Money.splitForDisplay(1L)).isEqualTo("0" to "01")
    }

    @Test
    fun `formatCompact converts cents to wan and yi correctly`() {
        assertThat(Money.formatCompact(0L)).isEqualTo("0.00")
        assertThat(Money.formatCompact(999_999L)).isEqualTo("9,999.99")
        assertThat(Money.formatCompact(1_000_000L)).isEqualTo("1万")
        assertThat(Money.formatCompact(1_234_567L)).isEqualTo("1.2万")
        assertThat(Money.formatCompact(100_000_000L)).isEqualTo("100万")
        assertThat(Money.formatCompact(10_000_000_000L)).isEqualTo("1亿")
        assertThat(Money.formatCompact(-12_345_600L)).isEqualTo("-12.3万")
    }
}
