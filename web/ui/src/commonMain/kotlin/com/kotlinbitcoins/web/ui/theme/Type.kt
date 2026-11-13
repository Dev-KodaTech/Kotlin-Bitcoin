package com.kotlinbitcoins.web.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.inter_medium
import com.kotlinbitcoins.web.ui.resources.inter_regular
import com.kotlinbitcoins.web.ui.resources.inter_semibold
import org.jetbrains.compose.resources.Font

/**
 * Inter (OFL, web/ui/INTER-OFL.txt), embedded because the canvas does not use system fonts and needs the pt-BR
 * glyphs (W-16). Subset: Latin, Latin-1 and – — ‘ ’ “ ” • … ↑ → ↓ −.
 */
@Composable
fun interFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
    )

/** Material 3 type scale with Inter in every style. */
@Composable
fun appTypography(): Typography {
    val family = interFontFamily()
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

/** Tabular figures, so that values line up (W-16). */
fun TextStyle.numeric(): TextStyle = copy(fontFeatureSettings = "tnum")
