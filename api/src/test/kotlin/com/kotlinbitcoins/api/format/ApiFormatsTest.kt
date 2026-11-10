package com.kotlinbitcoins.api.format

import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

class ApiFormatsTest {
    private val formats = ApiFormats(ZoneId.of("America/Sao_Paulo"))

    @Test
    fun `formats money with two decimal places`() {
        val money = (Money.of(BigDecimal("100.5")) as Outcome.Success).value

        assertThat(formats.money(money)).isEqualTo("100.50")
        assertThat(formats.money(Money.ZERO)).isEqualTo("0.00")
    }

    @Test
    fun `formats BTC with eight decimal places`() {
        assertThat(formats.btc(BtcAmount(100_000))).isEqualTo("0.00100000")
        assertThat(formats.btc(BtcAmount(250_000_000))).isEqualTo("2.50000000")
    }

    @Test
    fun `formats quote keeping the source scale`() {
        val price = (QuotePrice.of(BigDecimal("400000.00")) as Outcome.Success).value
        val precise = (QuotePrice.of(BigDecimal("400000.12345678")) as Outcome.Success).value

        assertThat(formats.quote(price)).isEqualTo("400000.00")
        assertThat(formats.quote(precise)).isEqualTo("400000.12345678")
    }

    @Test
    fun `formats instants in America Sao_Paulo with offset`() {
        assertThat(formats.instant(Instant.parse("2026-11-09T11:10:00Z"))).isEqualTo("2026-11-09T08:10:00-03:00")
        assertThat(formats.instant(Instant.parse("2026-11-09T11:10:05.123456Z")))
            .isEqualTo("2026-11-09T08:10:05-03:00")
    }
}
