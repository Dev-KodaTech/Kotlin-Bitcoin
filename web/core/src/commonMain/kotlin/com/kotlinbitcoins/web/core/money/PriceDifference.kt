package com.kotlinbitcoins.web.core.money

/** A signed difference between two quote prices, e.g. the spread (sell − buy), with every decimal of both. */
class PriceDifference private constructor(
    private val plain: String,
) {
    /** "R$ 1.000,00", "−R$ 5,00" (minus sign U+2212) or "R$ 0,00". */
    fun format(): String {
        val body = BRL_PREFIX + PtBrNumberFormat.format(plain.removePrefix("-"))
        return if (plain.startsWith('-')) "$MINUS_SIGN$body" else body
    }

    override fun equals(other: Any?): Boolean = other is PriceDifference && other.plain == plain

    override fun hashCode(): Int = plain.hashCode()

    override fun toString(): String = "PriceDifference($plain)"

    companion object {
        private const val MINUS_SIGN = "−"

        /** `to − from`, exact. */
        fun between(
            from: QuotePrice,
            to: QuotePrice,
        ): PriceDifference {
            val scale = maxOf(from.displayDecimals(), to.displayDecimals())
            return PriceDifference(DecimalSupport.subtract(to.toPlainString(), from.toPlainString(), scale))
        }
    }
}
