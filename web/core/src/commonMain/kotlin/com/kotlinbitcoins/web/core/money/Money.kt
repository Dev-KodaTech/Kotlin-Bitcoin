package com.kotlinbitcoins.web.core.money

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome

/** An amount in BRL: ≥ 0 with exactly 2 decimals (data-model §1). */
class Money private constructor(
    private val plain: String,
) : Comparable<Money> {
    /** The API representation (`MoneyInput` pattern), e.g. "1234.56". */
    fun toApiString(): String = plain

    /** "R$ 1.234,56", with a non-breaking space after the symbol (FR-036). */
    fun format(): String = BRL_PREFIX + PtBrNumberFormat.format(plain)

    /** Text for the amount field, as [com.kotlinbitcoins.web.core.format.AmountInputParser] reads it: "1.234,56". */
    fun toInputString(): String = PtBrNumberFormat.format(plain)

    /** Exact sum, used only for display totals (FR-029). */
    operator fun plus(other: Money): Money = Money(DecimalSupport.add(plain, other.plain, SCALE))

    fun isZero(): Boolean = DecimalSupport.isZero(plain)

    override fun compareTo(other: Money): Int = DecimalSupport.compare(plain, other.plain)

    override fun equals(other: Any?): Boolean = other is Money && other.plain == plain

    override fun hashCode(): Int = plain.hashCode()

    override fun toString(): String = "Money($plain)"

    companion object {
        internal const val SCALE = 2
        private val API_PATTERN = Regex("""^\d+\.\d{2}$""")

        val ZERO: Money = Money("0.00")

        /** Parses an API `Money`; a value outside the contract pattern is [ApiError.Unexpected] (FR-041). */
        fun parseApi(raw: String): Outcome<ApiError, Money> =
            if (API_PATTERN.matches(raw)) {
                Outcome.Success(Money(DecimalSupport.withScale(raw, SCALE)))
            } else {
                Outcome.Failure(ApiError.Unexpected)
            }

        /** For values already validated by [com.kotlinbitcoins.web.core.format.AmountInputParser]. */
        internal fun ofValidated(plain: String): Money = Money(DecimalSupport.withScale(plain, SCALE))
    }
}
