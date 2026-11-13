package com.kotlinbitcoins.web.data.browser

import com.kotlinbitcoins.web.state.port.Announcer

/**
 * Writes into the real `aria-live` regions of index.html, outside the canvas (W-09; technique chosen in
 * "W-00 Resultado", because the Compose `liveRegion` does not produce its own region). The text is cleared and set
 * again on the next frame, so that an identical message is announced again.
 */
class DomAnnouncer : Announcer {
    override fun polite(text: String) = announceInRegion(POLITE_REGION, text)

    override fun assertive(text: String) = announceInRegion(ASSERTIVE_REGION, text)

    private companion object {
        const val POLITE_REGION = "live-polite"
        const val ASSERTIVE_REGION = "live-assertive"
    }
}
