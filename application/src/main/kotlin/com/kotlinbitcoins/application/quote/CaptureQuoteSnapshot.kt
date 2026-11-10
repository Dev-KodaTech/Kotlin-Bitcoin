package com.kotlinbitcoins.application.quote

import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.QuoteCache
import com.kotlinbitcoins.application.port.QuoteHistoryRepository
import com.kotlinbitcoins.application.port.QuoteProvider
import com.kotlinbitcoins.domain.quote.QuoteSnapshot
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.map
import com.kotlinbitcoins.domain.shared.mapError

/**
 * Records the quote of the current 10 minute slot (US10, FR-027, FR-028). It calls the source directly: a history
 * point must be a real quote of its slot, so the 60 s fallback of [QuoteService] does not apply. When the source
 * fails nothing is saved and the slot stays empty; the history never interpolates.
 * The quote also refreshes the shared cache, since it is a fresh answer from the source.
 */
class CaptureQuoteSnapshot(
    private val provider: QuoteProvider,
    private val history: QuoteHistoryRepository,
    private val cache: QuoteCache,
    private val clock: Clock,
) {
    fun execute(): Outcome<QuoteHistoryError, QuoteSnapshot> {
        val slotAt = QuoteSnapshot.slotFor(clock.now())
        return when (val fetched = provider.fetchCurrent()) {
            is Outcome.Failure -> {
                Outcome.Failure(QuoteHistoryError.SourceUnavailable)
            }

            is Outcome.Success -> {
                val snapshot = QuoteSnapshot.from(fetched.value, slotAt)
                history
                    .saveIfAbsent(snapshot)
                    .map {
                        cache.put(fetched.value)
                        snapshot
                    }.mapError<_, _, QuoteHistoryError> { QuoteHistoryError.ServiceUnavailable }
            }
        }
    }
}
