package com.kotlinbitcoins.domain.investment

import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.InvalidAmount
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import com.kotlinbitcoins.domain.shared.QuotePrice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BtcConversionTest {
    @Test
    fun `purchase converts reais into satoshis at the sell price`() {
        val result = BtcConversion.purchase(positive("400.00"), price("400000.00"))

        assertThat(result).isEqualTo(Outcome.Success(BtcAmount(100_000)))
    }

    @Test
    fun `purchase rounds down to the satoshi`() {
        // 100.00 / 300000.00 = 0.000333333... BTC = 33_333.33 sats
        val result = BtcConversion.purchase(positive("100.00"), price("300000.00"))

        assertThat(result).isEqualTo(Outcome.Success(BtcAmount(33_333)))
    }

    @Test
    fun `purchase uses the quote with the exact scale received`() {
        // 1.00 / 123456.12345678 BTC = 810.0... sats
        val result = BtcConversion.purchase(positive("1.00"), price("123456.12345678"))

        assertThat(result).isEqualTo(Outcome.Success(BtcAmount(810)))
    }

    @Test
    fun `purchase worth less than one satoshi is an invalid amount`() {
        // 0.01 / 2000000.00 = 0.000000005 BTC = 0.5 sat
        val result = BtcConversion.purchase(positive("0.01"), price("2000000.00"))

        assertThat(result).isEqualTo(Outcome.Failure(InvalidAmount))
    }

    @Test
    fun `purchase of exactly one satoshi is accepted`() {
        // 0.01 / 1000000.00 = 0.00000001 BTC = 1 sat
        val result = BtcConversion.purchase(positive("0.01"), price("1000000.00"))

        assertThat(result).isEqualTo(Outcome.Success(BtcAmount(1)))
    }

    @Test
    fun `purchase beyond the satoshi range is an invalid amount instead of overflowing`() {
        val result = BtcConversion.purchase(positive("99999999999999999.99"), price("0.00000001"))

        assertThat(result).isEqualTo(Outcome.Failure(InvalidAmount))
    }

    @Test
    fun `currentValue multiplies the bitcoin by the buy price`() {
        val result = BtcConversion.currentValue(BtcAmount(100_000), price("440000.00"))

        assertThat(result).isEqualTo(money("440.00"))
    }

    @Test
    fun `currentValue rounds down to the cent`() {
        // 33_333 sats × 300000.00 ÷ 10⁸ = 99.999
        val result = BtcConversion.currentValue(BtcAmount(33_333), price("300000.00"))

        assertThat(result).isEqualTo(money("99.99"))
    }

    @Test
    fun `currentValue of a few satoshis can be worth zero`() {
        // 1 sat × 400000.00 ÷ 10⁸ = 0.004
        val result = BtcConversion.currentValue(BtcAmount(1), price("400000.00"))

        assertThat(result).isEqualTo(Money.ZERO)
    }

    @Test
    fun `currentValue uses the exact scale of the quote`() {
        // 100_000 sats × 123456.12345678 ÷ 10⁸ = 123.45612345678
        val result = BtcConversion.currentValue(BtcAmount(100_000), price("123456.12345678"))

        assertThat(result).isEqualTo(money("123.45"))
    }

    @Test
    fun `satsToCover converts the amount into the satoshis that cover it at the buy price`() {
        // 50000.00 / 200000.00 = 0.25 BTC
        val result = BtcConversion.satsToCover(money("50000.00"), price("200000.00"))

        assertThat(result).isEqualTo(BtcAmount(25_000_000))
    }

    @Test
    fun `satsToCover rounds up to the satoshi so the amount is always covered`() {
        // 100.00 / 300000.00 = 0.000333333... BTC = 33_333.33 sats
        val result = BtcConversion.satsToCover(money("100.00"), price("300000.00"))

        assertThat(result).isEqualTo(BtcAmount(33_334))
    }

    @Test
    fun `satsToCover of an exact amount is not rounded`() {
        val result = BtcConversion.satsToCover(money("100.00"), price("400000.00"))

        assertThat(result).isEqualTo(BtcAmount(25_000))
    }

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value

    private fun positive(value: String): PositiveMoney = (PositiveMoney.of(BigDecimal(value)) as Outcome.Success).value

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}
