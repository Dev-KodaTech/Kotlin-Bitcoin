package com.kotlinbitcoins.domain.shared

import java.math.BigDecimal
import java.math.RoundingMode

/** Amount entered or produced that is not a valid money value (FR-008, FR-013, FR-018). */
data object InvalidAmount

/**
 * Amount in reais: [BigDecimal] with scale exactly 2, never negative (FR-010, FR-032).
 * Values with more than two significant decimal places are rejected, never rounded silently;
 * rounding only happens through [floorOf], which states the rounding mode explicitly.
 */
class Money private constructor(
    val amount: BigDecimal,
) : Comparable<Money> {
    init {
        require(amount.scale() == SCALE) { "Money must have scale $SCALE" }
        require(amount.signum() >= 0) { "Money cannot be negative" }
    }

    operator fun plus(other: Money): Money = Money(amount.add(other.amount))

    operator fun minus(other: Money): Money = Money(amount.subtract(other.amount))

    override fun compareTo(other: Money): Int = amount.compareTo(other.amount)

    override fun equals(other: Any?): Boolean = other is Money && amount == other.amount

    override fun hashCode(): Int = amount.hashCode()

    override fun toString(): String = amount.toPlainString()

    companion object {
        const val SCALE = 2

        val ZERO = Money(BigDecimal.ZERO.setScale(SCALE))

        fun of(value: BigDecimal): Outcome<InvalidAmount, Money> =
            when {
                value.signum() < 0 -> Outcome.Failure(InvalidAmount)
                value.stripTrailingZeros().scale() > SCALE -> Outcome.Failure(InvalidAmount)
                else -> Outcome.Success(Money(value.setScale(SCALE, RoundingMode.UNNECESSARY)))
            }

        /** Rounds down to the cent (FR-017, FR-019a, FR-020). */
        fun floorOf(value: BigDecimal): Money = Money(value.setScale(SCALE, RoundingMode.FLOOR))
    }
}
