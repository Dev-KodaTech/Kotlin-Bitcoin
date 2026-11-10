package com.kotlinbitcoins.domain.quote

import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class QuoteSnapshotTest {
    @Test
    fun `slotFor truncates an instant to the previous multiple of 10 minutes`() {
        val slot = QuoteSnapshot.slotFor(Instant.parse("2026-11-09T11:17:42Z"))

        assertThat(slot).isEqualTo(Instant.parse("2026-11-09T11:10:00Z"))
    }

    @Test
    fun `slotFor drops the sub-second part`() {
        val slot = QuoteSnapshot.slotFor(Instant.parse("2026-11-09T11:19:59.999Z"))

        assertThat(slot).isEqualTo(Instant.parse("2026-11-09T11:10:00Z"))
    }

    @Test
    fun `an instant that is already aligned does not change`() {
        val aligned = Instant.parse("2026-11-09T11:10:00Z")

        assertThat(QuoteSnapshot.slotFor(aligned)).isEqualTo(aligned)
    }

    @Test
    fun `the slot changes exactly at the 10 minute boundary`() {
        assertThat(QuoteSnapshot.slotFor(Instant.parse("2026-11-09T11:19:59Z")))
            .isEqualTo(Instant.parse("2026-11-09T11:10:00Z"))
        assertThat(QuoteSnapshot.slotFor(Instant.parse("2026-11-09T11:20:00Z")))
            .isEqualTo(Instant.parse("2026-11-09T11:20:00Z"))
    }

    @Test
    fun `from copies the prices and takes the instant the quote was obtained as capturedAt`() {
        val quote = Quote(price("399000.00"), price("400000.12345678"), Instant.parse("2026-11-09T11:17:42Z"))
        val slotAt = Instant.parse("2026-11-09T11:10:00Z")

        val snapshot = QuoteSnapshot.from(quote, slotAt)

        assertThat(snapshot.slotAt).isEqualTo(slotAt)
        assertThat(snapshot.buyPrice).isEqualTo(price("399000.00"))
        assertThat(snapshot.sellPrice).isEqualTo(price("400000.12345678"))
        assertThat(snapshot.capturedAt).isEqualTo(Instant.parse("2026-11-09T11:17:42Z"))
    }

    @Test
    fun `a slot that is not a multiple of 10 minutes is rejected`() {
        val quote = Quote(price("399000.00"), price("400000.00"), Instant.parse("2026-11-09T11:17:42Z"))

        assertThatThrownBy { QuoteSnapshot.from(quote, Instant.parse("2026-11-09T11:17:00Z")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}
