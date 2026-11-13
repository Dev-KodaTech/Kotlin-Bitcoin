package com.kotlinbitcoins.web.state.port

/** Screen reader announcements through real `aria-live` regions outside the canvas (W-09, FR-047). */
interface Announcer {
    fun polite(text: String)

    fun assertive(text: String)
}
