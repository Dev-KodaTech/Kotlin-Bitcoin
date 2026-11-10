package com.kotlinbitcoins.domain.shared

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal

class PositiveMoneyTest {
    @ParameterizedTest
    @ValueSource(strings = ["0", "0.00", "-5", "10.123"])
    fun `rejects zero, negative and more than two decimal places`(value: String) {
        assertThat(PositiveMoney.of(BigDecimal(value))).isEqualTo(Outcome.Failure(InvalidAmount))
    }

    @ParameterizedTest
    @ValueSource(strings = ["0.01", "100.00", "100.5"])
    fun `accepts positive values with up to two decimal places`(value: String) {
        val result = PositiveMoney.of(BigDecimal(value))

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        val positive = (result as Outcome.Success).value
        assertThat(positive.amount).isEqualByComparingTo(BigDecimal(value))
        assertThat(positive.money.amount.scale()).isEqualTo(2)
    }
}
