package com.kotlinbitcoins.web.core.money

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome

/** A bitcoin price in BRL: > 0, keeping the 0 to 8 decimals received from the API (data-model §1). */
class QuotePrice private constructor(
    private val plain: String,
) : Comparable<QuotePrice> {
    /** The value exactly as received (canonical integer part), e.g. "400000.12345". */
    fun toPlainString(): String = plain

    /** "R$ 400.000,12345": every received decimal, with a minimum of 2 and no rounding (FR-036). */
    fun format(): String = BRL_PREFIX + formatNumber()

    /** "400.000,12345": [format] without the currency symbol, for the value tags of the chart (W-13). */
    fun formatNumber(): String = PtBrNumberFormat.format(DecimalSupport.withScale(plain, displayDecimals()))

    /**
     * A mark of the price axis: "R$ 400.500" when it has no cents, else as [format]. Still exact: only zero
     * decimals are left out.
     */
    fun formatAxis(): String =
        if (plain.substringAfter('.', missingDelimiterValue = "").all { it == '0' }) {
            BRL_PREFIX + PtBrNumberFormat.format(DecimalSupport.withScale(plain, 0))
        } else {
            format()
        }

    internal fun displayDecimals(): Int =
        maxOf(plain.substringAfter('.', missingDelimiterValue = "").length, MIN_DECIMALS)

    override fun compareTo(other: QuotePrice): Int = DecimalSupport.compare(plain, other.plain)

    override fun equals(other: Any?): Boolean = other is QuotePrice && other.plain == plain

    override fun hashCode(): Int = plain.hashCode()

    override fun toString(): String = "QuotePrice($plain)"

    companion object {
        private const val MIN_DECIMALS = 2
        private val API_PATTERN = Regex("""^\d+(\.\d{1,8})?$""")

        /**
         * A mark of the price axis between [min] and [max] (`step` of `steps`), truncated to 2 decimals; null when
         * the truncation reaches zero (a price is always > 0). The ends are [min] and [max] themselves.
         */
        internal fun axisMark(
            min: QuotePrice,
            max: QuotePrice,
            step: Int,
            steps: Int,
        ): QuotePrice? =
            when (step) {
                0 -> {
                    min
                }

                steps -> {
                    max
                }

                else -> {
                    DecimalSupport
                        .interpolateFloor(min.plain, max.plain, step, steps, MIN_DECIMALS)
                        .takeUnless(DecimalSupport::isZero)
                        ?.let(::QuotePrice)
                }
            }

        /** A mark computed by [PriceAxis]: exact, > 0 and with at most 2 decimals. */
        internal fun axisValue(plain: String): QuotePrice = QuotePrice(DecimalSupport.withScale(plain, MIN_DECIMALS))

        fun parseApi(raw: String): Outcome<ApiError, QuotePrice> {
            if (!API_PATTERN.matches(raw) || DecimalSupport.isZero(raw)) return Outcome.Failure(ApiError.Unexpected)
            val integerPart = DecimalSupport.canonicalInteger(raw.substringBefore('.'))
            val fraction = raw.substringAfter('.', missingDelimiterValue = "")
            return Outcome.Success(QuotePrice(if (fraction.isEmpty()) integerPart else "$integerPart.$fraction"))
        }
    }
}
