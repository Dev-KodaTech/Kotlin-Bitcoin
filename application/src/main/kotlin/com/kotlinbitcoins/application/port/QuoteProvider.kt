package com.kotlinbitcoins.application.port

import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.Outcome

/** The quote source did not give a usable quote. [reason] is for logs only: no stack trace, no response body. */
data class QuoteProviderError(
    val reason: String,
)

/** External source of the BTC quote. [Quote.quotedAt] is the instant the answer was received (FR-012a). */
fun interface QuoteProvider {
    fun fetchCurrent(): Outcome<QuoteProviderError, Quote>
}
