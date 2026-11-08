package com.ai.assistance.operit.ui.common.a2ui

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*

internal data class CardChartAxis(val ticks: List<Double>) {
    val min get() = ticks.first()
    val max get() = ticks.last()
}

/** Rounded, evenly spaced ticks, including zero for bars and padding constant series. */
internal fun cardChartAxis(low: Double, high: Double, includeZero: Boolean): CardChartAxis {
    var min = if (includeZero) minOf(low, 0.0) else low
    var max = if (includeZero) maxOf(high, 0.0) else high
    if (min == max) {
        val padding = if (min == 0.0) 1.0 else maxOf(abs(min) * .1, 1e-300)
        if (!includeZero) min -= padding
        max += padding
    }
    val raw = maxOf((max - min) / 4, Math.ulp(maxOf(abs(min), abs(max))), 1e-300)
    val exponent = floor(log10(raw)).toInt()
    val magnitude = 10.0.pow(exponent)
    val fraction = raw / magnitude
    val factor = listOf(1.0, 2.0, 2.5, 5.0, 10.0).firstOrNull { it >= fraction } ?: 10.0
    // Decimal arithmetic avoids labels such as 0.30000000000000004.
    val decimalStep = BigDecimal.valueOf(factor).scaleByPowerOfTen(exponent)
    val start = BigDecimal.valueOf(min).divide(decimalStep, 0, RoundingMode.FLOOR).toLong()
    val end = BigDecimal.valueOf(max).divide(decimalStep, 0, RoundingMode.CEILING).toLong()
    return CardChartAxis((0..(end - start).toInt().coerceIn(1, 8)).map {
        decimalStep.multiply(BigDecimal.valueOf(start + it)).toDouble()
    })
}

internal fun cardChartNumber(value: Double): String {
    if (value == 0.0) return "0"
    val number = BigDecimal.valueOf(value).stripTrailingZeros()
    return if (abs(value) < 1e-6) number.toString() else number.toPlainString()
}

/** Share of a pie slice with at most one decimal; tiny nonzero shares never read as 0. */
internal fun cardChartPercent(part: Double, total: Double): String {
    if (total <= 0.0 || part <= 0.0) return "0"
    val share = BigDecimal.valueOf(part / total * 100).setScale(1, RoundingMode.HALF_UP)
    return if (share.signum() == 0) "<0.1" else share.stripTrailingZeros().toPlainString()
}

internal fun cardChartTickLabels(ticks: List<Double>): List<String> {
    val largest = ticks.maxOf { abs(it) }
    val (scale, suffix) = when {
        largest >= 1e9 -> 1e9 to "B"
        largest >= 1e6 -> 1e6 to "M"
        largest >= 1e4 -> 1e3 to "K"
        else -> 1.0 to ""
    }
    val compact = ticks.map {
        BigDecimal.valueOf(it).divide(BigDecimal.valueOf(scale))
            .setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + suffix
    }
    return if (compact.distinct().size == ticks.size) compact else ticks.map(::cardChartNumber)
}

internal fun cardChartLineIndex(x: Float, left: Float, width: Float, count: Int): Int =
    if (count <= 1) 0 else (((x - left) / width.coerceAtLeast(1f)) * (count - 1))
        .roundToInt().coerceIn(0, count - 1)

/** Each bar owns its full-height horizontal slot, including whitespace above short/zero bars. */
internal fun cardChartBarSelection(x: Float, left: Float, width: Float, categories: Int, series: Int): Pair<Int, Int> {
    val slot = width.coerceAtLeast(1f) / categories.coerceAtLeast(1)
    val category = floor((x - left) / slot).toInt().coerceIn(0, categories - 1)
    val groupStart = left + slot * (category + .15f)
    val seriesIndex = floor((x - groupStart) / (slot * .7f / series)).toInt().coerceIn(0, series - 1)
    return seriesIndex to category
}
