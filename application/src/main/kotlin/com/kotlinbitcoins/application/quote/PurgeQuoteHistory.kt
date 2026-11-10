package com.kotlinbitcoins.application.quote

import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.QuoteHistoryRepository
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.mapError
import java.time.Duration

/** Removes the snapshots with more than 90 days (US10, FR-029) and answers how many it removed. */
class PurgeQuoteHistory(
    private val history: QuoteHistoryRepository,
    private val clock: Clock,
) {
    fun execute(): Outcome<QuoteHistoryError.ServiceUnavailable, Int> =
        history
            .deleteOlderThan(clock.now().minus(RETENTION))
            .mapError { QuoteHistoryError.ServiceUnavailable }

    companion object {
        val RETENTION: Duration = Duration.ofDays(90)
    }
}
