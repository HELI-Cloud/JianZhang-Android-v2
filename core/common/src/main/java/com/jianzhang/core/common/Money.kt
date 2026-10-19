package com.jianzhang.core.common

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Money {
    private val symbols = DecimalFormatSymbols(Locale.CHINA).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }

    fun format(cents: Long, showSign: Boolean = false): String {
        val value = BigDecimal.valueOf(cents, 2)
        val text = DecimalFormat("#,##0.00", symbols).format(value.abs())
        return when {
            !showSign -> if (cents < 0) "-$text" else text
            cents > 0 -> "+$text"
            cents < 0 -> "-$text"
            else -> text
        }
    }

    /**
     * Chinese compact notation. Input and thresholds are in cents:
     * 1 万 = 1,000,000 cents, 1 亿 = 10,000,000,000 cents.
     */
    fun formatCompact(cents: Long): String {
        val yuan = BigDecimal.valueOf(cents, 2)
        val abs = yuan.abs()
        return when {
            abs >= BigDecimal("100000000") -> compact(yuan, BigDecimal("100000000"), "亿")
            abs >= BigDecimal("10000") -> compact(yuan, BigDecimal("10000"), "万")
            else -> format(cents, false)
        }
    }

    private fun compact(value: BigDecimal, unit: BigDecimal, suffix: String): String {
        val scaled = value.divide(unit, 1, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        return scaled + suffix
    }

    /**
     * Compact whole-yuan label for a gauge axis. Under 10,000 yuan the label
     * keeps grouping separators; above that it switches to 万/亿 so a 320dp
     * phone can show three labels without wrapping.
     */
    fun formatAxis(cents: Long): String {
        if (kotlin.math.abs(cents) >= 1_000_000L) return formatCompact(cents)
        val yuan = BigDecimal.valueOf(cents, 2).setScale(0, RoundingMode.HALF_UP)
        return DecimalFormat("#,##0", symbols).format(yuan)
    }

    fun parseToCents(raw: String): Long? {
        val text = raw.trim().replace(",", "").removePrefix("¥").removePrefix("￥")
        if (text.isEmpty()) return null
        return runCatching {
            BigDecimal(text).setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
        }.getOrNull()
    }

    fun splitForDisplay(cents: Long): Pair<String, String> {
        val text = format(kotlin.math.abs(cents), false)
        val parts = text.split('.')
        return parts[0] to parts.getOrElse(1) { "00" }
    }
}