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
    fun format(): String {
        val decimals = plain.substringAfter('.', missingDelimiterValue = "").length
        return BRL_PREFIX + PtBrNumberFormat.format(DecimalSupport.withScale(plain, maxOf(decimals, MIN_DECIMALS)))
    }

    override fun compareTo(other: QuotePrice): Int = DecimalSupport.compare(plain, other.plain)

    override fun equals(other: Any?): Boolean = other is QuotePrice && other.plain == plain

    override fun hashCode(): Int = plain.hashCode()

    override fun toString(): String = "QuotePrice($plain)"

    companion object {
        private const val MIN_DECIMALS = 2
        private val API_PATTERN = Regex("""^\d+(\.\d{1,8})?$""")

        fun parseApi(raw: String): Outcome<ApiError, QuotePrice> {
            if (!API_PATTERN.matches(raw) || DecimalSupport.isZero(raw)) return Outcome.Failure(ApiError.Unexpected)
            val integerPart = DecimalSupport.canonicalInteger(raw.substringBefore('.'))
            val fraction = raw.substringAfter('.', missingDelimiterValue = "")
            return Outcome.Success(QuotePrice(if (fraction.isEmpty()) integerPart else "$integerPart.$fraction"))
        }
    }
}
