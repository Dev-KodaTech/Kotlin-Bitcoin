package com.kotlinbitcoins.web.state.shared

import com.kotlinbitcoins.web.state.port.Announcer

/**
 * Announces (polite) only the transitions of the quote availability (FR-047): available → unavailable and
 * unavailable → available. Repeated cycles and the first load say nothing. The texts come from :ui.
 */
class QuoteAvailabilityTracker(
    private val announcer: Announcer,
    private val unavailableText: String,
    private val restoredText: String,
) {
    private var available: Boolean? = null

    fun onQuoteState(state: QuoteState) {
        val now =
            when (state) {
                is QuoteState.Available -> true
                QuoteState.Unavailable -> false
                QuoteState.Loading, is QuoteState.Failed -> return
            }
        when (available) {
            true -> if (!now) announcer.polite(unavailableText)
            false -> if (now) announcer.polite(restoredText)
            null -> Unit
        }
        available = now
    }
}
