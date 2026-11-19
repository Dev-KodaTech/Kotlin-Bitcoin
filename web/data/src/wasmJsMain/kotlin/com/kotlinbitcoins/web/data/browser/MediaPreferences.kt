package com.kotlinbitcoins.web.data.browser

import com.kotlinbitcoins.web.state.port.ThemeMode
import com.kotlinbitcoins.web.state.port.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * `prefers-reduced-motion` and `prefers-color-scheme`, updated when the system setting changes, and the theme chosen
 * in the app, kept in `localStorage` under `kb.theme` ("light" or "dark"; absent = follow the system). Only this
 * choice goes to `localStorage`: the session stays in `sessionStorage` (FR-008). Other tabs follow the change. A
 * blocked storage keeps the choice for this tab only.
 */
class MediaPreferences : UserPreferences {
    override val prefersReducedMotion: StateFlow<Boolean> = media("(prefers-reduced-motion: reduce)")

    override val prefersDark: StateFlow<Boolean> = media("(prefers-color-scheme: dark)")

    private val mode = MutableStateFlow(parse(guarded(fallback = null) { localStorageGet(KEY) }))

    override val themeMode: StateFlow<ThemeMode> = mode.asStateFlow()

    init {
        guarded(fallback = Unit) { onLocalStorageChange(KEY) { mode.value = parse(it) } }
    }

    override fun selectTheme(mode: ThemeMode) {
        this.mode.value = mode
        val stored = stored(mode)
        guarded(fallback = Unit) {
            if (stored == null) localStorageRemove(KEY) else localStorageSet(KEY, stored)
        }
        guarded(fallback = Unit) { setDocumentTheme(stored) }
    }

    private fun media(query: String): StateFlow<Boolean> {
        val matches = MutableStateFlow(mediaMatches(query))
        onMediaChange(query) { matches.value = it }
        return matches.asStateFlow()
    }

    private companion object {
        const val KEY = "kb.theme"
        const val LIGHT = "light"
        const val DARK = "dark"

        fun parse(value: String?): ThemeMode =
            when (value) {
                LIGHT -> ThemeMode.Light
                DARK -> ThemeMode.Dark
                else -> ThemeMode.System
            }

        fun stored(mode: ThemeMode): String? =
            when (mode) {
                ThemeMode.System -> null
                ThemeMode.Light -> LIGHT
                ThemeMode.Dark -> DARK
            }
    }
}
