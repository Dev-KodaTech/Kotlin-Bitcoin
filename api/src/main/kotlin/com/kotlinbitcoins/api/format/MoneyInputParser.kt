package com.kotlinbitcoins.api.format

import com.kotlinbitcoins.domain.shared.InvalidAmount
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import java.math.BigDecimal

/**
 * Strict parsing of a money amount received as a JSON string (R-10): digits, optionally a dot and 1 or 2 decimals.
 * No sign, spaces, comma or scientific notation. Zero is rejected by [PositiveMoney].
 */
object MoneyInputParser {
    private val FORMAT = Regex("^[0-9]+(\\.[0-9]{1,2})?$")

    fun parse(raw: String?): Outcome<InvalidAmount, PositiveMoney> =
        if (raw != null && FORMAT.matches(raw)) {
            PositiveMoney.of(BigDecimal(raw))
        } else {
            Outcome.Failure(InvalidAmount)
        }
}
