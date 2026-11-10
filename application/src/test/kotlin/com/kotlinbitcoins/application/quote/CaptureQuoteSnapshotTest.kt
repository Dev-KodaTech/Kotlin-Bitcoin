package com.kotlinbitcoins.application.quote

import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.QuoteCache
import com.kotlinbitcoins.application.port.QuoteHistoryRepository
import com.kotlinbitcoins.application.port.QuoteProvider
import com.kotlinbitcoins.application.port.QuoteProviderError
import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.quote.QuoteSnapshot
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class CaptureQuoteSnapshotTest {
    // The job fires at 11:20:00 but the source answers a moment later, so the slot comes from the clock.
    private val now = Instant.parse("2026-11-09T11:20:00Z")
    private val provider = mockk<QuoteProvider>()
    private val history = mockk<QuoteHistoryRepository>()
    private val cache = mockk<QuoteCache>()
    private val clock = Clock { now }

    private val capture = CaptureQuoteSnapshot(provider, history, cache, clock)

    init {
        every { cache.put(any()) } just runs
    }

    @Test
    fun `calls the source directly and saves the quote in the slot of the clock`() {
        val quote = quote(quotedAt = now.plusMillis(350))
        every { provider.fetchCurrent() } returns Outcome.Success(quote)
        val saved = slot<QuoteSnapshot>()
        every { history.saveIfAbsent(capture(saved)) } returns Outcome.Success(true)

        val result = capture.execute()

        assertThat(saved.captured.slotAt).isEqualTo(Instant.parse("2026-11-09T11:20:00Z"))
        assertThat(saved.captured.buyPrice).isEqualTo(quote.buyPrice)
        assertThat(saved.captured.sellPrice).isEqualTo(quote.sellPrice)
        assertThat(saved.captured.capturedAt).isEqualTo(quote.quotedAt)
        assertThat(result).isEqualTo(Outcome.Success(saved.captured))
        verify(exactly = 1) { provider.fetchCurrent() }
    }

    @Test
    fun `puts the fresh quote in the shared cache`() {
        val quote = quote(quotedAt = now)
        every { provider.fetchCurrent() } returns Outcome.Success(quote)
        every { history.saveIfAbsent(any()) } returns Outcome.Success(true)

        capture.execute()

        verify(exactly = 1) { cache.put(quote) }
    }

    @Test
    fun `a slot that already exists is not an error`() {
        every { provider.fetchCurrent() } returns Outcome.Success(quote(quotedAt = now))
        every { history.saveIfAbsent(any()) } returns Outcome.Success(false)

        assertThat(capture.execute()).isInstanceOf(Outcome.Success::class.java)
    }

    @Test
    fun `when the source fails nothing is saved or cached and the point is omitted`() {
        every { provider.fetchCurrent() } returns Outcome.Failure(QuoteProviderError("timeout"))

        val result = capture.execute()

        assertThat(result).isEqualTo(Outcome.Failure(QuoteHistoryError.SourceUnavailable))
        verify(exactly = 0) { history.saveIfAbsent(any()) }
        verify(exactly = 0) { cache.put(any()) }
    }

    @Test
    fun `does not fall back to a cached quote`() {
        every { provider.fetchCurrent() } returns Outcome.Failure(QuoteProviderError("timeout"))
        every { cache.get() } returns quote(quotedAt = now.minusSeconds(30))

        capture.execute()

        verify(exactly = 0) { cache.get() }
        verify(exactly = 0) { history.saveIfAbsent(any()) }
    }

    @Test
    fun `an infrastructure failure becomes ServiceUnavailable with no partial write`() {
        every { provider.fetchCurrent() } returns Outcome.Success(quote(quotedAt = now))
        every { history.saveIfAbsent(any()) } returns Outcome.Failure(InfrastructureFailure("database down"))

        val result = capture.execute()

        assertThat(result).isEqualTo(Outcome.Failure(QuoteHistoryError.ServiceUnavailable))
        verify(exactly = 0) { cache.put(any()) }
    }

    private fun quote(quotedAt: Instant): Quote = Quote(price("399000.00"), price("400000.00"), quotedAt)

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}
