package com.kotlinbitcoins.web.state.support

import com.kotlinbitcoins.web.state.port.Announcer

class FakeAnnouncer : Announcer {
    sealed interface Announcement {
        data class Polite(
            val text: String,
        ) : Announcement

        data class Assertive(
            val text: String,
        ) : Announcement
    }

    val announcements = mutableListOf<Announcement>()

    override fun polite(text: String) {
        announcements += Announcement.Polite(text)
    }

    override fun assertive(text: String) {
        announcements += Announcement.Assertive(text)
    }
}
