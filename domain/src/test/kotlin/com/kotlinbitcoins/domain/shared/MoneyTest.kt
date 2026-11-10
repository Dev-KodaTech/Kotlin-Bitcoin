package com.kotlinbitcoins.domain.shared

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyTest {
    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value

    @Test
    fun `of normalizes to scale 2`() {
        val result = Money.of(BigDecimal("100.5"))

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        val amount = (result as Outcome.Success).value.amount
        assertThat(amount).isEqualTo(BigDecimal("100.50"))
        assertThat(amount.scale()).isEqualTo(2)
    }

    @Test
    fun `of accepts whole numbers and trailing zeros beyond two places`() {
        assertThat(money("100").amount).isEqualTo(BigDecimal("100.00"))
        assertThat(money("10.1200").amount).isEqualTo(BigDecimal("10.12"))
    }

    @Test
    fun `of rejects more than two significant decimal places`() {
        assertThat(Money.of(BigDecimal("10.123"))).isEqualTo(Outcome.Failure(InvalidAmount))
    }

    @Test
    fun `of rejects negative values`() {
        assertThat(Money.of(BigDecimal("-0.01"))).isEqualTo(Outcome.Failure(InvalidAmount))
    }

    @Test
    fun `floorOf rounds down to the cent`() {
        assertThat(Money.floorOf(BigDecimal("439.999")).amount).isEqualTo(BigDecimal("439.99"))
        assertThat(Money.floorOf(BigDecimal("0.009")).amount).isEqualTo(BigDecimal("0.00"))
    }

    @Test
    fun `floorOf rejects negative values`() {
        assertThatThrownBy { Money.floorOf(BigDecimal("-1")) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `plus and minus keep scale 2`() {
        assertThat((money("100.50") + money("0.75")).amount).isEqualTo(BigDecimal("101.25"))
        assertThat((money("100.50") - money("100.50")).amount).isEqualTo(BigDecimal("0.00"))
    }

    @Test
    fun `minus that would go negative violates the invariant`() {
        assertThatThrownBy { money("1.00") - money("1.01") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `compareTo orders by amount`() {
        assertThat(money("10.00")).isLessThan(money("10.01"))
        assertThat(money("10")).isEqualByComparingTo(money("10.00"))
        assertThat(money("10")).isEqualTo(money("10.00"))
    }

    @Test
    fun `ZERO is zero with scale 2`() {
        assertThat(Money.ZERO.amount).isEqualTo(BigDecimal("0.00"))
    }
}
