package com.kotlinbitcoins.web.core.money

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode

/**
 * The only place that touches bignum (W-06). Values travel as plain decimal strings ("1234.56", "-5.00"),
 * already validated by the value objects; [BigDecimal] is used only for exact arithmetic and comparison.
 */
internal object DecimalSupport {
    private const val DIVISION_PRECISION = 40L
    private const val PERCENT = 100

    fun compare(
        left: String,
        right: String,
    ): Int = decimal(left).compareTo(decimal(right))

    fun isZero(plain: String): Boolean = decimal(plain).isZero()

    fun add(
        left: String,
        right: String,
        scale: Int,
    ): String = withScale(decimal(left).add(decimal(right)).toPlainString(), scale)

    fun subtract(
        left: String,
        right: String,
        scale: Int,
    ): String = withScale(decimal(left).subtract(decimal(right)).toPlainString(), scale)

    /** `base + step × times`, exact: the marks of the price axis after the first one (W-13). */
    fun addMultiple(
        base: String,
        step: String,
        times: Int,
        scale: Int,
    ): String = withScale(decimal(base).add(decimal(step).multiply(BigDecimal.fromInt(times))).toPlainString(), scale)

    /** The largest multiple of [step] that is ≤ [value]. */
    fun floorToMultiple(
        value: String,
        step: String,
        scale: Int,
    ): String = toMultiple(value, step, scale, RoundingMode.FLOOR)

    /** The smallest multiple of [step] that is ≥ [value]. */
    fun ceilToMultiple(
        value: String,
        step: String,
        scale: Int,
    ): String = toMultiple(value, step, scale, RoundingMode.CEILING)

    /** How many [step]s fit from [low] to [high], both multiples of [step]. */
    fun stepsBetween(
        low: String,
        high: String,
        step: String,
    ): Int {
        val mode = DecimalMode(DIVISION_PRECISION, RoundingMode.FLOOR, 0)
        return withScale(decimal(high).subtract(decimal(low)).divide(decimal(step), mode).toPlainString(), 0).toInt()
    }

    /** `(last − first) × 100 ÷ first`, truncated toward zero to [scale] decimals: a variation in percent. */
    fun percentChange(
        first: String,
        last: String,
        scale: Int,
    ): String {
        val mode = DecimalMode(DIVISION_PRECISION, RoundingMode.TOWARDS_ZERO, scale.toLong())
        val change = decimal(last).subtract(decimal(first)).multiply(BigDecimal.fromInt(PERCENT))
        return withScale(change.divide(decimal(first), mode).toPlainString(), scale)
    }

    /** `dividend ÷ divisor` truncated (FLOOR) to [scale] decimals, used only by the labeled estimates (FR-015). */
    fun divideFloor(
        dividend: String,
        divisor: String,
        scale: Int,
    ): String {
        val mode = DecimalMode(DIVISION_PRECISION, RoundingMode.FLOOR, scale.toLong())
        return withScale(decimal(dividend).divide(decimal(divisor), mode).toPlainString(), scale)
    }

    /**
     * `min + (max − min) × step ÷ steps`, truncated (FLOOR) to [scale] decimals: the intermediate marks of the price
     * axis of the chart (W-13). Exact decimal arithmetic, never floating point.
     */
    fun interpolateFloor(
        min: String,
        max: String,
        step: Int,
        steps: Int,
        scale: Int,
    ): String {
        val mode = DecimalMode(DIVISION_PRECISION, RoundingMode.FLOOR, scale.toLong())
        val offset =
            decimal(max)
                .subtract(decimal(min))
                .multiply(BigDecimal.fromInt(step))
                .divide(BigDecimal.fromInt(steps), mode)
        return withScale(decimal(min).add(offset).toPlainString(), scale)
    }

    /**
     * Rewrites a plain decimal with exactly [scale] decimals and a canonical integer part. Extra decimals are
     * truncated, which never happens for the exact values of the value objects.
     */
    fun withScale(
        plain: String,
        scale: Int,
    ): String {
        val negative = plain.startsWith('-')
        val unsigned = plain.removePrefix("-").removePrefix("+")
        val integerPart = canonicalInteger(unsigned.substringBefore('.'))
        val fraction = unsigned.substringAfter('.', missingDelimiterValue = "").take(scale).padEnd(scale, '0')
        val body = if (scale == 0) integerPart else "$integerPart.$fraction"
        val isZero = integerPart == "0" && fraction.all { it == '0' }
        return if (negative && !isZero) "-$body" else body
    }

    /** Removes leading zeros, keeping at least one digit. */
    fun canonicalInteger(digits: String): String = digits.trimStart('0').ifEmpty { "0" }

    private fun toMultiple(
        value: String,
        step: String,
        scale: Int,
        rounding: RoundingMode,
    ): String {
        val quotient = decimal(value).divide(decimal(step), DecimalMode(DIVISION_PRECISION, rounding, 0))
        return withScale(quotient.multiply(decimal(step)).toPlainString(), scale)
    }

    private fun decimal(plain: String): BigDecimal = BigDecimal.parseString(plain)
}
