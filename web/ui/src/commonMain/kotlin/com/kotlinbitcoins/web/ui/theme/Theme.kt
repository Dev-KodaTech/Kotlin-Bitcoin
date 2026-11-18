package com.kotlinbitcoins.web.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.state.port.UserPreferences

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** Generous corners of the "Azul suave" look: fields and focus (small), buttons (medium), cards (large), shell. */
internal val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp),
    )

/**
 * Light or dark scheme from the system (`prefers-color-scheme`), updated at run time through [UserPreferences];
 * there is no manual switch (Out of Scope). Also provides [LocalReducedMotion] and the font of the values.
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
        LocalNumericFontFamily provides interFontFamily(),
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = appTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Color of the focus indicator drawn on the canvas (A2 of W-00); ≥ 3:1 against `surface` (ThemeContrastTest). */
fun focusColor(scheme: ColorScheme): Color = scheme.primary
