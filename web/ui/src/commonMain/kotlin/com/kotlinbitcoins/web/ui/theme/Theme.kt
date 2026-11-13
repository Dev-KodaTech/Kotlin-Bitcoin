package com.kotlinbitcoins.web.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kotlinbitcoins.web.state.port.UserPreferences

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/**
 * Light or dark scheme from the system (`prefers-color-scheme`), updated at run time through [UserPreferences];
 * there is no manual switch (Out of Scope). Also provides [LocalReducedMotion].
 */
@Composable
fun AppTheme(
    preferences: UserPreferences? = null,
    content: @Composable () -> Unit,
) {
    val dark = preferences?.prefersDark?.collectAsState()?.value ?: isSystemInDarkTheme()
    val reducedMotion = preferences?.prefersReducedMotion?.collectAsState()?.value ?: false
    CompositionLocalProvider(
        LocalExtendedColors provides if (dark) DarkExtendedColors else LightExtendedColors,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = appTypography(),
            content = content,
        )
    }
}

/** Color of the focus indicator drawn on the canvas (A2 of W-00); ≥ 3:1 against `surface` (ThemeContrastTest). */
fun focusColor(scheme: ColorScheme): Color = scheme.primary
