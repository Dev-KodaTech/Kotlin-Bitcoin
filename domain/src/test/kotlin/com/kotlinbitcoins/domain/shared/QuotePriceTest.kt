package com.kotlinbitcoins.domain.shared

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal

class QuotePriceTest {
    @ParameterizedTest
    @ValueSource(strings = ["400000.00", "400000.12345678", "400000"])
    fun `accepts positive prices keeping exactly the received scale`(value: String) {
        val result = QuotePrice.of(BigDecimal(value))

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        val price = (result as Outcome.Success).value.value
        assertThat(price).isEqualTo(BigDecimal(value))
        assertThat(price.scale()).isEqualTo(BigDecimal(value).scale())
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "0.00", "-1", "400000.123456789"])
    fun `rejects zero, negative and scale above 8`(value: String) {
        assertThat(QuotePrice.of(BigDecimal(value))).isEqualTo(Outcome.Failure(InvalidQuotePrice))
    }
}
