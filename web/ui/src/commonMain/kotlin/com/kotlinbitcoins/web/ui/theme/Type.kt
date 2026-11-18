package com.kotlinbitcoins.web.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.inter_medium
import com.kotlinbitcoins.web.ui.resources.inter_regular
import com.kotlinbitcoins.web.ui.resources.inter_semibold
import com.kotlinbitcoins.web.ui.resources.poppins_medium
import com.kotlinbitcoins.web.ui.resources.poppins_regular
import com.kotlinbitcoins.web.ui.resources.poppins_semibold
import org.jetbrains.compose.resources.Font

/**
 * Poppins (OFL, web/ui/POPPINS-OFL.txt): the face of every text. Embedded because the canvas does not use system
 * fonts and needs the pt-BR glyphs (W-16). Subset: Latin, Latin-1 and – — ‘ ’ “ ” • … −.
 */
@Composable
fun poppinsFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.poppins_regular, FontWeight.Normal),
        Font(Res.font.poppins_medium, FontWeight.Medium),
        Font(Res.font.poppins_semibold, FontWeight.SemiBold),
    )

/**
 * Inter (OFL, web/ui/INTER-OFL.txt): the face of the values ([numeric]). Poppins has no tabular figures and no
 * arrows; Inter has both. Subset: Latin, Latin-1 and – — ‘ ’ “ ” • … ↑ → ↓ −.
 */
@Composable
fun interFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
    )

/** The font of [numeric]; provided by AppTheme. */
val LocalNumericFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/** Material 3 type scale in Poppins, with semibold headings and titles. */
@Composable
fun appTypography(): Typography {
    val family = poppinsFontFamily()
    val base = Typography()

    fun TextStyle.text(): TextStyle = copy(fontFamily = family)

    fun TextStyle.heading(): TextStyle = copy(fontFamily = family, fontWeight = FontWeight.SemiBold)
    return Typography(
        displayLarge = base.displayLarge.heading(),
        displayMedium = base.displayMedium.heading(),
        displaySmall = base.displaySmall.heading().copy(letterSpacing = (-0.5).sp),
        headlineLarge = base.headlineLarge.heading(),
        headlineMedium = base.headlineMedium.heading().copy(letterSpacing = (-0.25).sp),
        headlineSmall = base.headlineSmall.heading(),
        titleLarge = base.titleLarge.heading(),
        titleMedium = base.titleMedium.heading(),
        titleSmall = base.titleSmall.heading(),
        bodyLarge = base.bodyLarge.text().copy(letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.text().copy(letterSpacing = 0.sp),
        bodySmall = base.bodySmall.text().copy(letterSpacing = 0.sp),
        labelLarge = base.labelLarge.text().copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelMedium = base.labelMedium.text().copy(letterSpacing = 0.sp),
        labelSmall = base.labelSmall.text().copy(letterSpacing = 0.sp),
    )
}

/** A value: Inter with tabular figures, so that values line up (W-16). The weight and the size stay. */
@Composable
fun TextStyle.numeric(): TextStyle = copy(fontFamily = LocalNumericFontFamily.current, fontFeatureSettings = "tnum")
