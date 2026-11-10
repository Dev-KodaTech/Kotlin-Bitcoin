package com.kotlinbitcoins.api.format

import com.kotlinbitcoins.domain.shared.InvalidAmount
import com.kotlinbitcoins.domain.shared.Outcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal

class MoneyInputParserTest {
    @ParameterizedTest
    @CsvSource("100, 100.00", "100.5, 100.50", "100.50, 100.50", "0.01, 0.01")
    fun `accepts positive amounts with up to two decimal places`(
        input: String,
        expected: String,
    ) {
        val result = MoneyInputParser.parse(input)

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        assertThat((result as Outcome.Success).value.amount).isEqualTo(BigDecimal(expected))
    }

    @Test
    fun `rejects null`() {
        assertThat(MoneyInputParser.parse(null)).isEqualTo(Outcome.Failure(InvalidAmount))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "0", "0.00", "-5", "abc", "10.123", "1e3", "+10", " 10", "10 ", "10.", ".5", "1,50"])
    fun `rejects anything outside the strict format or not positive`(input: String) {
        assertThat(MoneyInputParser.parse(input)).isEqualTo(Outcome.Failure(InvalidAmount))
    }
}
