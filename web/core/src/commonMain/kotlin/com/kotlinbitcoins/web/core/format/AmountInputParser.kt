package com.kotlinbitcoins.web.core.format

import com.kotlinbitcoins.web.core.error.AmountInputError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.money.Money

/**
 * Parses an amount typed in pt-BR (FR-039, W-11): "1234,56", "1.234,56", "1234" or "1.234".
 * Only format is checked here; the API stays the authority on the value.
 */
object AmountInputParser {
    /** Up to 17 integer digits: NUMERIC(19,2) of the 001. */
    private const val MAX_INTEGER_DIGITS = 17
    private const val MAX_DECIMALS = 2

    private val WITH_GROUPING = Regex("""^\d{1,3}(\.\d{3})+(,\d+)?$""")
    private val WITHOUT_GROUPING = Regex("""^\d+(,\d+)?$""")

    fun parse(input: String): Outcome<AmountInputError, Money> {
        val text = input.trim()
        val error =
            when {
                text.isEmpty() -> AmountInputError.Empty
                !WITH_GROUPING.matches(text) && !WITHOUT_GROUPING.matches(text) -> AmountInputError.InvalidFormat
                else -> null
            }
        if (error != null) return Outcome.Failure(error)

        val integerDigits = text.substringBefore(',').replace(".", "")
        val decimals = text.substringAfter(',', missingDelimiterValue = "")
        return when {
            integerDigits.length > MAX_INTEGER_DIGITS -> {
                Outcome.Failure(AmountInputError.InvalidFormat)
            }

            decimals.length > MAX_DECIMALS -> {
                Outcome.Failure(AmountInputError.TooManyDecimals)
            }

            else -> {
                val money = Money.ofValidated("$integerDigits.$decimals")
                if (money.isZero()) Outcome.Failure(AmountInputError.NotPositive) else Outcome.Success(money)
            }
        }
    }
}
