package com.kotlinbitcoins.web.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * "Azul suave" palette: a blue-tinted ground, a lighter shell on top of it, white cards and one indigo accent.
 * The schemes are hand-picked constants; nothing is generated at run time (Principle V). The Material 3 roles keep
 * their meaning (primary is the color of actions, links and focus); what Material 3 has no role for (the ground
 * behind the shell, cards, the hero block) lives in [ExtendedColors]. ThemeContrastTest checks every pair in use.
 */

internal val LightColors: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF3D5AF1),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDDE9FF),
        onPrimaryContainer = Color(0xFF14183A),
        inversePrimary = Color(0xFFAEBBFF),
        secondary = Color(0xFF4A5578),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDDE9FF),
        onSecondaryContainer = Color(0xFF14183A),
        tertiary = Color(0xFF1668BD),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD9ECFF),
        onTertiaryContainer = Color(0xFF0B2A4A),
        background = Color(0xFFE4ECFB),
        onBackground = Color(0xFF14183A),
        surface = Color(0xFFF6F9FF),
        onSurface = Color(0xFF14183A),
        surfaceVariant = Color(0xFFE1E8F7),
        onSurfaceVariant = Color(0xFF5A6386),
        surfaceTint = Color(0xFF3D5AF1),
        inverseSurface = Color(0xFF14183A),
        inverseOnSurface = Color(0xFFEEF2FF),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFCE4E1),
        onErrorContainer = Color(0xFF7A1410),
        outline = Color(0xFF6B7599),
        outlineVariant = Color(0xFFD5DEF2),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF6F9FF),
        surfaceContainer = Color(0xFFEEF3FD),
        surfaceContainerHigh = Color(0xFFE8EEFB),
        surfaceContainerHighest = Color(0xFFE1E8F7),
        surfaceContainerLow = Color(0xFFF9FBFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFD5DEF2),
    )

internal val DarkColors: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFAEBBFF),
        onPrimary = Color(0xFF0F1A66),
        primaryContainer = Color(0xFF24306A),
        onPrimaryContainer = Color(0xFFDCE3FF),
        inversePrimary = Color(0xFF3D5AF1),
        secondary = Color(0xFFC3CBEA),
        onSecondary = Color(0xFF1B2347),
        secondaryContainer = Color(0xFF24306A),
        onSecondaryContainer = Color(0xFFDCE3FF),
        tertiary = Color(0xFF9CCBFF),
        onTertiary = Color(0xFF003258),
        tertiaryContainer = Color(0xFF1D3B66),
        onTertiaryContainer = Color(0xFFD9ECFF),
        background = Color(0xFF090D1F),
        onBackground = Color(0xFFEEF2FF),
        surface = Color(0xFF10162F),
        onSurface = Color(0xFFEEF2FF),
        surfaceVariant = Color(0xFF2A3462),
        onSurfaceVariant = Color(0xFFA9B3D6),
        surfaceTint = Color(0xFFAEBBFF),
        inverseSurface = Color(0xFFEEF2FF),
        inverseOnSurface = Color(0xFF14183A),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8791B8),
        outlineVariant = Color(0xFF2A3462),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF262F5E),
        surfaceContainer = Color(0xFF19214A),
        surfaceContainerHigh = Color(0xFF1F2853),
        surfaceContainerHighest = Color(0xFF262F5E),
        surfaceContainerLow = Color(0xFF141B38),
        surfaceContainerLowest = Color(0xFF070A18),
        surfaceDim = Color(0xFF10162F),
    )

/**
 * Colors outside the M3 roles: rise/fall of a variation (FR-020), the two price series of the chart (W-13) and the
 * surfaces of the "Azul suave" look: [card] over the shell, [tint] for soft blocks and icon wells, and the [hero]
 * block (balance, current menu item) with its texts and its white actions.
 */
@Immutable
data class ExtendedColors(
    val success: Color,
    val decline: Color,
    val chartBuy: Color,
    val chartSell: Color,
    val card: Color,
    val tint: Color,
    val hero: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val heroAction: Color,
    val onHeroAction: Color,
    /** Soft colored shadow under cards; transparent in the dark theme, where cards separate by color alone. */
    val cardShadow: Color,
)

private val Hero = Color(0xFF3D5AF1)
private val OnHero = Color(0xFFFFFFFF)
private val OnHeroMuted = Color(0xFFEEF1FF)
private val OnHeroAction = Color(0xFF1C2A8C)

internal val LightExtendedColors =
    ExtendedColors(
        success = Color(0xFF0B7445),
        decline = Color(0xFFB93226),
        chartBuy = Color(0xFF3D5AF1),
        chartSell = Color(0xFFA85F00),
        card = Color(0xFFFFFFFF),
        tint = Color(0xFFDDE9FF),
        hero = Hero,
        onHero = OnHero,
        onHeroMuted = OnHeroMuted,
        heroAction = Color(0xFFFFFFFF),
        onHeroAction = OnHeroAction,
        cardShadow = Color(0x333D5AF1),
    )

internal val DarkExtendedColors =
    ExtendedColors(
        success = Color(0xFF5FD8A0),
        decline = Color(0xFFFF9488),
        chartBuy = Color(0xFFAEBBFF),
        chartSell = Color(0xFFFFB866),
        card = Color(0xFF19214A),
        tint = Color(0xFF24306A),
        hero = Hero,
        onHero = OnHero,
        onHeroMuted = OnHeroMuted,
        heroAction = Color(0xFFFFFFFF),
        onHeroAction = OnHeroAction,
        cardShadow = Color(0x00000000),
    )
