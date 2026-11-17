package com.kotlinbitcoins.web.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * Material 3 color schemes generated once from the seed #F7931A (W-16) and committed as constants; nothing is
 * generated at run time (Principle V).
 * Tool: @material/material-color-utilities 0.3.0, the library behind the Material Theme Builder, with
 * SchemeTonalSpot (the Theme Builder default) and contrast level 0, on 2026-11-12.
 * Extended colors: customColor() of the same library; success (#2E7D32) and decline (#C62828) without
 * harmonization, so they stay green/red; chart series buy (#1565C0) and sell (#F7931A) harmonized with the seed.
 * The seed orange itself is never used for text (≈ 2.4:1 on white). ThemeContrastTest checks every pair in use.
 */

internal val LightColors: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF86521A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDCBF),
        onPrimaryContainer = Color(0xFF6A3B02),
        inversePrimary = Color(0xFFFEB876),
        secondary = Color(0xFF735943),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFDCBF),
        onSecondaryContainer = Color(0xFF59422D),
        tertiary = Color(0xFF596239),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFDDE8B3),
        onTertiaryContainer = Color(0xFF424A23),
        background = Color(0xFFFFF8F5),
        onBackground = Color(0xFF221A14),
        surface = Color(0xFFFFF8F5),
        onSurface = Color(0xFF221A14),
        surfaceVariant = Color(0xFFF2DFD1),
        onSurfaceVariant = Color(0xFF51443A),
        surfaceTint = Color(0xFF86521A),
        inverseSurface = Color(0xFF372F28),
        inverseOnSurface = Color(0xFFFDEEE3),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        outline = Color(0xFF837469),
        outlineVariant = Color(0xFFD5C3B6),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFF8F5),
        surfaceContainer = Color(0xFFFBEBE0),
        surfaceContainerHigh = Color(0xFFF5E5DB),
        surfaceContainerHighest = Color(0xFFEFE0D5),
        surfaceContainerLow = Color(0xFFFFF1E8),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFE6D7CD),
    )

internal val DarkColors: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFFEB876),
        onPrimary = Color(0xFF4B2800),
        primaryContainer = Color(0xFF6A3B02),
        onPrimaryContainer = Color(0xFFFFDCBF),
        inversePrimary = Color(0xFF86521A),
        secondary = Color(0xFFE2C0A4),
        onSecondary = Color(0xFF412C18),
        secondaryContainer = Color(0xFF59422D),
        onSecondaryContainer = Color(0xFFFFDCBF),
        tertiary = Color(0xFFC1CC99),
        onTertiary = Color(0xFF2C340F),
        tertiaryContainer = Color(0xFF424A23),
        onTertiaryContainer = Color(0xFFDDE8B3),
        background = Color(0xFF19120C),
        onBackground = Color(0xFFEFE0D5),
        surface = Color(0xFF19120C),
        onSurface = Color(0xFFEFE0D5),
        surfaceVariant = Color(0xFF51443A),
        onSurfaceVariant = Color(0xFFD5C3B6),
        surfaceTint = Color(0xFFFEB876),
        inverseSurface = Color(0xFFEFE0D5),
        inverseOnSurface = Color(0xFF372F28),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF9E8E81),
        outlineVariant = Color(0xFF51443A),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF403730),
        surfaceContainer = Color(0xFF261E18),
        surfaceContainerHigh = Color(0xFF312822),
        surfaceContainerHighest = Color(0xFF3C332C),
        surfaceContainerLow = Color(0xFF221A14),
        surfaceContainerLowest = Color(0xFF130D07),
        surfaceDim = Color(0xFF19120C),
    )

/** Colors outside the M3 roles: rise/fall of a variation (FR-020) and the two price series of the chart (W-13). */
@Immutable
data class ExtendedColors(
    val success: Color,
    val decline: Color,
    val chartBuy: Color,
    val chartSell: Color,
)

internal val LightExtendedColors =
    ExtendedColors(
        success = Color(0xFF1B6D24),
        decline = Color(0xFFB91D20),
        chartBuy = Color(0xFF3F56BA),
        chartSell = Color(0xFF8C4F00),
    )

internal val DarkExtendedColors =
    ExtendedColors(
        success = Color(0xFF88D982),
        decline = Color(0xFFFFB4AC),
        chartBuy = Color(0xFFB9C3FF),
        chartSell = Color(0xFFFFB874),
    )
