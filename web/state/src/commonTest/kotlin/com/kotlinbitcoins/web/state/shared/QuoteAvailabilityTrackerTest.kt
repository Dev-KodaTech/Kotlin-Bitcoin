package com.kotlinbitcoins.web.state.shared

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.state.support.FakeAnnouncer
import com.kotlinbitcoins.web.state.support.FakeAnnouncer.Announcement.Polite
import com.kotlinbitcoins.web.state.support.quote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuoteAvailabilityTrackerTest {
    private val announcer = FakeAnnouncer()
    private val tracker =
        QuoteAvailabilityTracker(
            announcer = announcer,
            unavailableText = { "Cotação indisponível no momento" },
            restoredText = { "Cotação restabelecida" },
        )

    @Test
    fun theTextsAreReadWhenAnnouncedNotWhenCreated() {
        // On the web the string resources load asynchronously: the first composition sees "" (regression of T137).
        var unavailable = ""
        val late = QuoteAvailabilityTracker(announcer, unavailableText = { unavailable }, restoredText = { "" })
        late.onQuoteState(QuoteState.Available(quote()))
        unavailable = "Cotação indisponível no momento"

        late.onQuoteState(QuoteState.Unavailable)

        assertEquals(
            listOf<FakeAnnouncer.Announcement>(Polite("Cotação indisponível no momento")),
            announcer.announcements,
        )
    }

    @Test
    fun nothingOnTheFirstAvailableLoadNorOnRepeatedCycles() {
        tracker.onQuoteState(QuoteState.Loading)
        tracker.onQuoteState(QuoteState.Available(quote()))
        tracker.onQuoteState(QuoteState.Available(quote(buy = "400001.00")))
        tracker.onQuoteState(QuoteState.Available(quote(buy = "400002.00")))
        assertTrue(announcer.announcements.isEmpty())
    }

    @Test
    fun announcesOnlyTheTransitions() {
        tracker.onQuoteState(QuoteState.Available(quote()))
        tracker.onQuoteState(QuoteState.Unavailable)
        tracker.onQuoteState(QuoteState.Unavailable)
        tracker.onQuoteState(QuoteState.Unavailable)
        tracker.onQuoteState(QuoteState.Available(quote()))
        tracker.onQuoteState(QuoteState.Available(quote()))
        assertEquals(
            listOf<FakeAnnouncer.Announcement>(
                Polite("Cotação indisponível no momento"),
                Polite("Cotação restabelecida"),
            ),
            announcer.announcements,
        )
    }

    @Test
    fun firstLoadUnavailableIsNotATransition() {
        tracker.onQuoteState(QuoteState.Unavailable)
        assertTrue(announcer.announcements.isEmpty())
        tracker.onQuoteState(QuoteState.Available(quote()))
        assertEquals(listOf<FakeAnnouncer.Announcement>(Polite("Cotação restabelecida")), announcer.announcements)
    }

    @Test
    fun loadingAndOtherFailuresDoNotChangeTheTrackedAvailability() {
        tracker.onQuoteState(QuoteState.Available(quote()))
        tracker.onQuoteState(QuoteState.Failed(ApiError.NoConnection))
        tracker.onQuoteState(QuoteState.Loading)
        tracker.onQuoteState(QuoteState.Available(quote()))
        assertTrue(announcer.announcements.isEmpty())
    }
}
