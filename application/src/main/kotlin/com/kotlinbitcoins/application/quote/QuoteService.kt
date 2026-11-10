package com.kotlinbitcoins.application.quote

import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.QuoteCache
import com.kotlinbitcoins.application.port.QuoteProvider
import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.Outcome
import java.time.Duration
import java.time.Instant

/**
 * Obtains the quote used by one operation (R-06, FR-012, FR-012a). A single quote is fetched per operation, so the
 * quote an operation records is the one it used.
 *
 * 1. A cached quote younger than [REUSE_WINDOW] is used as is, with no call to the source.
 * 2. Otherwise the source is called; on success the quote is cached and used.
 * 3. If the source fails and the cache holds a quote of up to [FALLBACK_WINDOW], that quote is used, still carrying
 *    its original instant.
 * 4. Otherwise the result is [QuoteError.QuoteUnavailable].
 */
class QuoteService(
    private val provider: QuoteProvider,
    private val cache: QuoteCache,
    private val clock: Clock,
) {
    fun currentQuote(): Outcome<QuoteError.QuoteUnavailable, Quote> {
        val now = clock.now()
        val cached = cache.get()
        return if (cached != null && cached.ageAt(now) < REUSE_WINDOW) {
            Outcome.Success(cached)
        } else {
            when (val fetched = provider.fetchCurrent()) {
                is Outcome.Success -> {
                    cache.put(fetched.value)
                    Outcome.Success(fetched.value)
                }

                is Outcome.Failure -> {
                    fallback(cached, now)
                }
            }
        }
    }

    private fun fallback(
        cached: Quote?,
        now: Instant,
    ): Outcome<QuoteError.QuoteUnavailable, Quote> =
        if (cached != null && cached.ageAt(now) <= FALLBACK_WINDOW) {
            Outcome.Success(cached)
        } else {
            Outcome.Failure(QuoteError.QuoteUnavailable)
        }

    companion object {
        /** A cached quote younger than this is reused without calling the source. */
        val REUSE_WINDOW: Duration = Duration.ofSeconds(10)

        /** The oldest quote that may still be served when the source fails. */
        val FALLBACK_WINDOW: Duration = Duration.ofSeconds(60)
    }
}
