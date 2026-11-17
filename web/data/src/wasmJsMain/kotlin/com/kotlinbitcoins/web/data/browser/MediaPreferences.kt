package com.kotlinbitcoins.web.data.browser

import com.kotlinbitcoins.web.state.port.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** `prefers-reduced-motion` and `prefers-color-scheme`, updated when the system setting changes. */
class MediaPreferences : UserPreferences {
    override val prefersReducedMotion: StateFlow<Boolean> = media("(prefers-reduced-motion: reduce)")

    override val prefersDark: StateFlow<Boolean> = media("(prefers-color-scheme: dark)")

    private fun media(query: String): StateFlow<Boolean> {
        val matches = MutableStateFlow(mediaMatches(query))
        onMediaChange(query) { matches.value = it }
        return matches.asStateFlow()
    }
}
