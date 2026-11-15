package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.money.Trend
import com.kotlinbitcoins.web.core.money.VariationPercent
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.variation_down
import com.kotlinbitcoins.web.ui.resources.variation_flat
import com.kotlinbitcoins.web.ui.resources.variation_up
import com.kotlinbitcoins.web.ui.theme.LocalExtendedColors
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.stringResource

/**
 * Variation of an investment (FR-020): never by color alone. `Up` → "↑ +10,00% alta", `Down` → "↓ −5,00% queda",
 * `Flat` → "– 0,00% sem variação". The symbol and the value take the rise/fall color of the theme.
 */
@Composable
fun VariationBadge(
    variation: VariationPercent,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val color = variation.trend.color()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(variation.trend.symbol(), style = style, color = color)
        Text(variation.format(), style = style.numeric(), color = color)
        Text(variation.trend.text(), style = style)
    }
}

/** "+10,00%, alta": the variation as read in a full sentence (cards and announcements). */
@Composable
fun VariationPercent.spokenText(): String = "${format()}, ${trend.text()}"

@Composable
private fun Trend.text(): String =
    stringResource(
        when (this) {
            Trend.Up -> Res.string.variation_up
            Trend.Down -> Res.string.variation_down
            Trend.Flat -> Res.string.variation_flat
        },
    )

private fun Trend.symbol(): String =
    when (this) {
        Trend.Up -> "↑"
        Trend.Down -> "↓"
        Trend.Flat -> "–"
    }

@Composable
private fun Trend.color(): Color =
    when (this) {
        Trend.Up -> LocalExtendedColors.current.success
        Trend.Down -> LocalExtendedColors.current.decline
        Trend.Flat -> LocalContentColor.current
    }
