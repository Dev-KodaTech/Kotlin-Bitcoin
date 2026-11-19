package com.kotlinbitcoins.web.core.money

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome

/** Direction of a variation, for the icon and the text of FR-020. */
enum class Trend { Up, Down, Flat }

/** A signed percentage with exactly 2 decimals (`variationPercent` of the API). */
class VariationPercent private constructor(
    private val plain: String,
) {
    val trend: Trend =
        when {
            DecimalSupport.isZero(plain) -> Trend.Flat
            plain.startsWith('-') -> Trend.Down
            else -> Trend.Up
        }

    /** "+10,00%", "−5,00%" (minus sign U+2212) or "0,00%". */
    fun format(): String {
        val number = PtBrNumberFormat.format(plain.removePrefix("-"))
        val sign =
            when (trend) {
                Trend.Up -> "+"
                Trend.Down -> MINUS_SIGN
                Trend.Flat -> ""
            }
        return "$sign$number%"
    }

    override fun equals(other: Any?): Boolean = other is VariationPercent && other.plain == plain

    override fun hashCode(): Int = plain.hashCode()

    override fun toString(): String = "VariationPercent($plain)"

    companion object {
        private const val SCALE = 2
        private const val MINUS_SIGN = "−"
        private val API_PATTERN = Regex("""^-?\d+\.\d{2}$""")

        /** The variation from [first] to [last], truncated toward zero to 2 decimals (the chart's 24 h change). */
        fun between(
            first: QuotePrice,
            last: QuotePrice,
        ): VariationPercent =
            VariationPercent(DecimalSupport.percentChange(first.toPlainString(), last.toPlainString(), SCALE))

        fun parseApi(raw: String): Outcome<ApiError, VariationPercent> =
            if (API_PATTERN.matches(raw)) {
                Outcome.Success(VariationPercent(DecimalSupport.withScale(raw, SCALE)))
            } else {
                Outcome.Failure(ApiError.Unexpected)
            }
    }
}
