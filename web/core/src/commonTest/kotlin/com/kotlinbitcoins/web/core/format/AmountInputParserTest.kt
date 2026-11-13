package com.kotlinbitcoins.web.core.format

import com.kotlinbitcoins.web.core.error.AmountInputError
import com.kotlinbitcoins.web.core.support.failureError
import com.kotlinbitcoins.web.core.support.successValue
import kotlin.test.Test
import kotlin.test.assertEquals

/** Table of research.md W-11. */
class AmountInputParserTest {
    @Test
    fun `accepted inputs`() {
        val accepted =
            mapOf(
                "1234,56" to "1234.56",
                "1.234,56" to "1234.56",
                "1234" to "1234.00",
                "1.234" to "1234.00",
                " 100,00 " to "100.00",
                "0,01" to "0.01",
                "0,1" to "0.10",
                "12.345.678,9" to "12345678.90",
                "99999999999999999,99" to "99999999999999999.99",
            )
        accepted.forEach { (input, api) ->
            assertEquals(api, AmountInputParser.parse(input).successValue().toApiString(), "\"$input\"")
        }
    }

    @Test
    fun `refused inputs`() {
        val refused =
            mapOf(
                "" to AmountInputError.Empty,
                "   " to AmountInputError.Empty,
                "0" to AmountInputError.NotPositive,
                "0,00" to AmountInputError.NotPositive,
                "-5" to AmountInputError.InvalidFormat,
                "R$ -5" to AmountInputError.InvalidFormat,
                "1,234.56" to AmountInputError.InvalidFormat,
                "1e3" to AmountInputError.InvalidFormat,
                "1.23,4" to AmountInputError.InvalidFormat,
                "abc" to AmountInputError.InvalidFormat,
                "123456789012345678" to AmountInputError.InvalidFormat,
                "12,345" to AmountInputError.TooManyDecimals,
                "1.234,567" to AmountInputError.TooManyDecimals,
            )
        refused.forEach { (input, error) ->
            assertEquals(error, AmountInputParser.parse(input).failureError(), "\"$input\"")
        }
    }
}
