package com.kotlinbitcoins.application.quote

import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.QuoteHistoryRepository
import com.kotlinbitcoins.domain.quote.QuoteSnapshot
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.map
import com.kotlinbitcoins.domain.shared.mapError
import java.time.Duration

/**
 * Quote history of the last 24 hours in ascending order, at most 144 points of 10 minutes (US10, FR-028).
 * Slots that were not captured are simply absent. A read has no side effect, so it needs no transaction.
 */
class GetQuoteHistory(
    private val history: QuoteHistoryRepository,
    private val clock: Clock,
) {
    fun execute(): Outcome<QuoteHistoryError.ServiceUnavailable, List<QuoteSnapshot>> =
        history
            .findSince(clock.now().minus(WINDOW))
            .map { points -> points.sortedBy { it.slotAt }.takeLast(MAX_POINTS) }
            .mapError { QuoteHistoryError.ServiceUnavailable }

    companion object {
        val WINDOW: Duration = Duration.ofHours(24)
        const val MAX_POINTS = 144
    }
}
