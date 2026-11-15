package com.kotlinbitcoins.web.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/** WCAG 2.1 contrast of the generated color schemes (W-16): text ≥ 4.5:1, non-text ≥ 3:1, in both themes. */
class ThemeContrastTest {
    private val themes =
        listOf(
            "claro" to (LightColors to LightExtendedColors),
            "escuro" to (DarkColors to DarkExtendedColors),
        )

    @Test
    fun textPairsReachFourAndAHalfToOne() {
        themes.forEach { (name, colors) ->
            val (scheme, _) = colors
            textPairs(scheme).forEach { (pair, colorsOfPair) ->
                assertContrast(colorsOfPair.first, colorsOfPair.second, TEXT_MINIMUM, "$name: $pair")
            }
        }
    }

    /** VariationBadge writes the value in the rise/fall color, on the page and inside cards (US5). */
    @Test
    fun riseAndFallTextReachFourAndAHalfToOne() {
        themes.forEach { (name, colors) ->
            val (scheme, extended) = colors
            listOf("surface" to scheme.surface, "card" to scheme.surfaceContainerHighest).forEach { (bgName, bg) ->
                assertContrast(extended.success, bg, TEXT_MINIMUM, "$name: alta/$bgName")
                assertContrast(extended.decline, bg, TEXT_MINIMUM, "$name: queda/$bgName")
            }
        }
    }

    @Test
    fun nonTextColorsReachThreeToOne() {
        themes.forEach { (name, colors) ->
            val (scheme, extended) = colors
            val nonText =
                mapOf(
                    "outline/surface" to scheme.outline,
                    "foco (primary)/surface" to focusColor(scheme),
                    "alta/surface" to extended.success,
                    "queda/surface" to extended.decline,
                    "série compra/surface" to extended.chartBuy,
                    "série venda/surface" to extended.chartSell,
                )
            nonText.forEach { (pair, color) -> assertContrast(color, scheme.surface, NON_TEXT_MINIMUM, "$name: $pair") }
        }
    }

    private fun textPairs(scheme: ColorScheme): Map<String, Pair<Color, Color>> =
        mapOf(
            "onPrimary/primary" to (scheme.onPrimary to scheme.primary),
            "onPrimaryContainer/primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
            "onSecondary/secondary" to (scheme.onSecondary to scheme.secondary),
            "onSurface/surface" to (scheme.onSurface to scheme.surface),
            "onSurfaceVariant/surface" to (scheme.onSurfaceVariant to scheme.surface),
            "onSurface/surfaceContainer" to (scheme.onSurface to scheme.surfaceContainer),
            "onError/error" to (scheme.onError to scheme.error),
            "onErrorContainer/errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
            "primary/surface" to (scheme.primary to scheme.surface),
        )

    private fun assertContrast(
        foreground: Color,
        background: Color,
        minimum: Double,
        description: String,
    ) {
        val ratio = contrast(foreground, background)
        assertTrue(ratio >= minimum, "$description: ${(ratio * 100).toInt() / 100.0}:1 < $minimum:1")
    }

    private fun contrast(
        first: Color,
        second: Color,
    ): Double {
        val l1 = luminance(first)
        val l2 = luminance(second)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    /** Relative luminance of sRGB (WCAG 2.1). */
    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private companion object {
        const val TEXT_MINIMUM = 4.5
        const val NON_TEXT_MINIMUM = 3.0
    }
}
