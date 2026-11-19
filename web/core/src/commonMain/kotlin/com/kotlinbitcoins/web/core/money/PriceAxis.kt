package com.kotlinbitcoins.web.core.money

/**
 * Round marks of the price axis of the chart (W-13), computed with exact decimals: every mark is a multiple of one
 * "nice" step (1, 2, 2,5 or 5 × 10ⁿ, from R$ 0,01 up), so the axis reads "R$ 399.000, R$ 400.000, …" and the text
 * of each mark is exact. The first mark is the bottom of the axis and the last one its top.
 */
internal object PriceAxis {
    private const val SCALE = 2
    private const val MAX_INTERVALS = 5
    private const val MIN_EXPONENT = -2
    private const val MAX_EXPONENT = 12

    /** A flat axis spans 4 steps of about 1/800 of the price around it (R$ 399.000 to R$ 401.000 at R$ 400.000). */
    private const val FLAT_DIVISOR = "800"
    private const val FLAT_STEPS_BELOW = 2
    private const val FLAT_INTERVALS = 4

    /** 0,01; 0,02; 0,05; 0,1; …; 1; 2; 5; 10; 20; 25; 50; 100; … in increasing order. */
    private val steps: List<String> =
        (MIN_EXPONENT..MAX_EXPONENT).flatMap { exponent ->
            buildList {
                add(decimalOf("1", exponent))
                add(decimalOf("2", exponent))
                if (exponent >= 1) add(decimalOf("25", exponent - 1))
                add(decimalOf("5", exponent))
            }
        }

    /**
     * The marks from a multiple of the step below [min] to one above [max], at most 6; null when the bottom would
     * not be a price (≤ 0), and the caller keeps the plain range.
     */
    fun marks(
        min: QuotePrice,
        max: QuotePrice,
    ): List<QuotePrice>? =
        if (min == max) flatMarks(min.toPlainString()) else spanMarks(min.toPlainString(), max.toPlainString())

    private fun spanMarks(
        min: String,
        max: String,
    ): List<QuotePrice>? =
        steps.firstNotNullOfOrNull { step ->
            val low = DecimalSupport.floorToMultiple(min, step, SCALE)
            val high = DecimalSupport.ceilToMultiple(max, step, SCALE)
            val intervals = DecimalSupport.stepsBetween(low, high, step)
            if (intervals <= MAX_INTERVALS) marksFrom(low, step, intervals) else null
        }

    private fun flatMarks(value: String): List<QuotePrice>? {
        val target = DecimalSupport.divideFloor(value, FLAT_DIVISOR, SCALE)
        val step = steps.firstOrNull { DecimalSupport.compare(it, target) >= 0 } ?: steps.last()
        val center = DecimalSupport.floorToMultiple(value, step, SCALE)
        val low = DecimalSupport.addMultiple(center, step, -FLAT_STEPS_BELOW, SCALE)
        return marksFrom(low, step, FLAT_INTERVALS)
    }

    private fun marksFrom(
        low: String,
        step: String,
        intervals: Int,
    ): List<QuotePrice>? {
        if (DecimalSupport.compare(low, "0") <= 0) return null
        return (0..intervals).map { QuotePrice.axisValue(DecimalSupport.addMultiple(low, step, it, SCALE)) }
    }

    /** `digits × 10^exponent` as a plain decimal: ("1", -2) → "0.01", ("25", 1) → "250". */
    private fun decimalOf(
        digits: String,
        exponent: Int,
    ): String {
        if (exponent >= 0) return digits + "0".repeat(exponent)
        val places = -exponent
        val padded = digits.padStart(places + 1, '0')
        return padded.dropLast(places) + "." + padded.takeLast(places)
    }
}
