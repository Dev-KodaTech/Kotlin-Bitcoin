package com.kotlinbitcoins.application.quote

import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.Outcome

/** Current BTC quote in reais (US4, FR-011, FR-012, FR-012a). Delegates the window rules to [QuoteService]. */
class GetCurrentQuote(
    private val quoteService: QuoteService,
) {
    fun execute(): Outcome<QuoteError.QuoteUnavailable, Quote> = quoteService.currentQuote()
}
