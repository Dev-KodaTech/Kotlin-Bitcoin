package com.kotlinbitcoins.application.quote

import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class GetCurrentQuoteTest {
    private val quoteService = mockk<QuoteService>()

    private val getCurrentQuote = GetCurrentQuote(quoteService)

    @Test
    fun `delegates to the quote service and returns its quote`() {
        val quote = Quote(price("399000.00"), price("400000.00"), Instant.parse("2026-11-09T11:10:00Z"))
        every { quoteService.currentQuote() } returns Outcome.Success(quote)

        val result = getCurrentQuote.execute()

        assertThat(result).isEqualTo(Outcome.Success(quote))
        verify(exactly = 1) { quoteService.currentQuote() }
    }

    @Test
    fun `propagates QuoteUnavailable`() {
        every { quoteService.currentQuote() } returns Outcome.Failure(QuoteError.QuoteUnavailable)

        val result = getCurrentQuote.execute()

        assertThat(result).isEqualTo(Outcome.Failure(QuoteError.QuoteUnavailable))
    }

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}
