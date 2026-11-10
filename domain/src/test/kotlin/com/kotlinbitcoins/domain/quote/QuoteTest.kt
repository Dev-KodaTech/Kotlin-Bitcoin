package com.kotlinbitcoins.domain.quote

import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class QuoteTest {
    private val quotedAt = Instant.parse("2026-11-09T11:10:00Z")

    @Test
    fun `keeps buy price, sell price and the instant it was quoted`() {
        val quote = Quote(price("399000.00"), price("400000.00"), quotedAt)

        assertThat(quote.buyPrice).isEqualTo(price("399000.00"))
        assertThat(quote.sellPrice).isEqualTo(price("400000.00"))
        assertThat(quote.quotedAt).isEqualTo(quotedAt)
    }

    @Test
    fun `age is the time elapsed since the quote was taken`() {
        val quote = Quote(price("399000.00"), price("400000.00"), quotedAt)

        assertThat(quote.ageAt(quotedAt.plusSeconds(30))).isEqualTo(Duration.ofSeconds(30))
        assertThat(quote.ageAt(quotedAt.plusMillis(60_001))).isEqualTo(Duration.ofMillis(60_001))
    }

    @Test
    fun `a quote has no age at the instant it was taken`() {
        val quote = Quote(price("399000.00"), price("400000.00"), quotedAt)

        assertThat(quote.ageAt(quotedAt)).isEqualTo(Duration.ZERO)
    }

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}
