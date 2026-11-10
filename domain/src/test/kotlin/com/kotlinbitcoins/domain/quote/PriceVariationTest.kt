package com.kotlinbitcoins.domain.quote

import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PriceVariationTest {
    @Test
    fun `a rise of 10 percent`() {
        val result = PriceVariation.percent(price("400000.00"), price("440000.00"))

        assertThat(result.toPlainString()).isEqualTo("10.00")
    }

    @Test
    fun `a fall of 10 percent is negative`() {
        val result = PriceVariation.percent(price("400000.00"), price("360000.00"))

        assertThat(result.toPlainString()).isEqualTo("-10.00")
    }

    @Test
    fun `no change is 0_00`() {
        val result = PriceVariation.percent(price("400000.00"), price("400000.00"))

        assertThat(result.toPlainString()).isEqualTo("0.00")
    }

    @Test
    fun `rounds half up to two decimal places`() {
        // (400000 − 300000) ÷ 300000 × 100 = 33.333...
        assertThat(PriceVariation.percent(price("300000.00"), price("400000.00")).toPlainString()).isEqualTo("33.33")
        // (400100 − 400000) ÷ 400000 × 100 = 0.025 → 0.03
        assertThat(PriceVariation.percent(price("400000.00"), price("400100.00")).toPlainString()).isEqualTo("0.03")
    }

    @Test
    fun `uses the exact scale of both quotes`() {
        val result = PriceVariation.percent(price("100000.00000001"), price("110000.00000001"))

        assertThat(result.toPlainString()).isEqualTo("10.00")
    }

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}
