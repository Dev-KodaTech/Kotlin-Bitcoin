package com.kotlinbitcoins.web.state.port

import kotlinx.coroutines.flow.StateFlow

/** Media preferences of the system (`prefers-reduced-motion`, `prefers-color-scheme`). */
interface UserPreferences {
    val prefersReducedMotion: StateFlow<Boolean>
    val prefersDark: StateFlow<Boolean>
}
