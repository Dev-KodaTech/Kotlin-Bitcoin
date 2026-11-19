package com.kotlinbitcoins.web.state.port

import kotlinx.coroutines.flow.StateFlow

/**
 * Media preferences of the system (`prefers-reduced-motion`, `prefers-color-scheme`) and the theme the user chose in
 * the app ([themeMode]), which wins over the system when it is not [ThemeMode.System].
 */
interface UserPreferences {
    val prefersReducedMotion: StateFlow<Boolean>
    val prefersDark: StateFlow<Boolean>

    /** The choice of the theme switch; [ThemeMode.System] until the user picks another one. */
    val themeMode: StateFlow<ThemeMode>

    /** Applies [mode] at once and keeps it for the next visits in this browser. */
    fun selectTheme(mode: ThemeMode)
}

/** Theme picked in the app: follow the system, or always light, or always dark. */
enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    /** Whether the dark scheme is used, given the current system preference [systemDark]. */
    fun isDark(systemDark: Boolean): Boolean =
        when (this) {
            System -> systemDark
            Light -> false
            Dark -> true
        }
}
