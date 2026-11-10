package com.kotlinbitcoins.application.quote

import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.QuoteCache
import com.kotlinbitcoins.application.port.QuoteProvider
import com.kotlinbitcoins.application.port.QuoteProviderError
import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class QuoteServiceTest {
    private val now = Instant.parse("2026-11-09T11:10:00Z")
    private val provider = mockk<QuoteProvider>()
    private val cache = mockk<QuoteCache>()
    private val clock = mockk<Clock>()

    private val service = QuoteService(provider, cache, clock)

    init {
        every { clock.now() } returns now
        every { cache.put(any()) } just runs
    }

    @Test
    fun `a cached quote younger than 10 seconds is used without calling the source`() {
        val cached = quote(quotedAt = now.minusMillis(9_999))
        every { cache.get() } returns cached

        val result = service.currentQuote()

        assertThat(result).isEqualTo(Outcome.Success(cached))
        verify(exactly = 0) { provider.fetchCurrent() }
        verify(exactly = 0) { cache.put(any()) }
    }

    @Test
    fun `a cached quote with 10 seconds or more makes the service call the source and cache the new quote`() {
        val fresh = quote(quotedAt = now, sell = "401000.00")
        every { cache.get() } returns quote(quotedAt = now.minusSeconds(10))
        every { provider.fetchCurrent() } returns Outcome.Success(fresh)

        val result = service.currentQuote()

        assertThat(result).isEqualTo(Outcome.Success(fresh))
        verify(exactly = 1) { provider.fetchCurrent() }
        verify(exactly = 1) { cache.put(fresh) }
    }

    @Test
    fun `without a cached quote the source is called and the quote is cached`() {
        val fresh = quote(quotedAt = now)
        every { cache.get() } returns null
        every { provider.fetchCurrent() } returns Outcome.Success(fresh)

        val result = service.currentQuote()

        assertThat(result).isEqualTo(Outcome.Success(fresh))
        verify(exactly = 1) { cache.put(fresh) }
    }

    @Test
    fun `when the source fails a cached quote of 30 seconds is returned with its original instant`() {
        val cached = quote(quotedAt = now.minusSeconds(30))
        every { cache.get() } returns cached
        every { provider.fetchCurrent() } returns Outcome.Failure(QuoteProviderError("http 521"))

        val result = service.currentQuote()

        assertThat(result).isEqualTo(Outcome.Success(cached))
        assertThat((result as Outcome.Success).value.quotedAt).isEqualTo(now.minusSeconds(30))
        verify(exactly = 0) { cache.put(any()) }
    }

    @Test
    fun `when the source fails a cached quote of exactly 60 seconds is still returned`() {
        val cached = quote(quotedAt = now.minusSeconds(60))
        every { cache.get() } returns cached
        every { provider.fetchCurrent() } returns Outcome.Failure(QuoteProviderError("timeout"))

        val result = service.currentQuote()

        assertThat(result).isEqualTo(Outcome.Success(cached))
    }

    @Test
    fun `when the source fails a cached quote of 60 seconds and 1 millisecond is QuoteUnavailable`() {
        every { cache.get() } returns quote(quotedAt = now.minusMillis(60_001))
        every { provider.fetchCurrent() } returns Outcome.Failure(QuoteProviderError("timeout"))

        val result = service.currentQuote()

        assertThat(result).isEqualTo(Outcome.Failure(QuoteError.QuoteUnavailable))
    }

    @Test
    fun `when the source fails and there is no cached quote the result is QuoteUnavailable`() {
        every { cache.get() } returns null
        every { provider.fetchCurrent() } returns Outcome.Failure(QuoteProviderError("http 500"))

        val result = service.currentQuote()

        assertThat(result).isEqualTo(Outcome.Failure(QuoteError.QuoteUnavailable))
    }

    @Test
    fun `the cache is read once and the source is called at most once per operation`() {
        every { cache.get() } returns null
        every { provider.fetchCurrent() } returns Outcome.Failure(QuoteProviderError("http 500"))

        service.currentQuote()

        verify(exactly = 1) { cache.get() }
        verify(exactly = 1) { provider.fetchCurrent() }
    }

    private fun quote(
        quotedAt: Instant,
        buy: String = "399000.00",
        sell: String = "400000.00",
    ) = Quote(price(buy), price(sell), quotedAt)

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}
