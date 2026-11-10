package com.kotlinbitcoins.domain.shared

import java.math.BigDecimal

/** Money strictly greater than zero, built from client input (FR-008, FR-013, FR-018). */
@JvmInline
value class PositiveMoney private constructor(
    val money: Money,
) {
    val amount: BigDecimal get() = money.amount

    companion object {
        fun of(value: BigDecimal): Outcome<InvalidAmount, PositiveMoney> =
            Money.of(value).flatMap { money ->
                if (money > Money.ZERO) Outcome.Success(PositiveMoney(money)) else Outcome.Failure(InvalidAmount)
            }
    }
}
