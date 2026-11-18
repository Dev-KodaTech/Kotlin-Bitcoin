package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.model.Quote
import com.kotlinbitcoins.web.state.shared.QuoteState
import com.kotlinbitcoins.web.ui.icons.AppIcons
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.brasilia_time
import com.kotlinbitcoins.web.ui.resources.quote_at
import com.kotlinbitcoins.web.ui.resources.quote_buy
import com.kotlinbitcoins.web.ui.resources.quote_sell
import com.kotlinbitcoins.web.ui.resources.quote_title
import com.kotlinbitcoins.web.ui.resources.quote_unavailable
import com.kotlinbitcoins.web.ui.shared.message
import com.kotlinbitcoins.web.ui.theme.LocalExtendedColors
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.stringResource

private val TileMinWidth = 240.dp
private val IconWellSize = 48.dp

/**
 * Current quote (FR-013): the title with the instant (seconds, Brasília) and one tile for the buy price and one for
 * the sell price. "Unavailable" shows only the message, never old values (US2-3). No live region: periodic updates
 * are not announced (FR-047); the transitions are announced by QuoteAvailabilityTracker.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuoteBlock(
    state: QuoteState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            SectionTitle(stringResource(Res.string.quote_title))
            if (state is QuoteState.Available) QuoteInstant(state.quote)
        }
        when (state) {
            QuoteState.Loading -> SectionCard { LoadingIndicator() }
            is QuoteState.Available -> QuoteTiles(state.quote)
            QuoteState.Unavailable -> SectionCard { Text(stringResource(Res.string.quote_unavailable)) }
            is QuoteState.Failed -> SectionCard { ErrorBlock(state.error.message(), onRetry = onRetry) }
        }
    }
}

@Composable
private fun QuoteInstant(quote: Quote) {
    Text(
        text =
            stringResource(
                Res.string.quote_at,
                quote.quotedAt.formatWithSeconds(),
                stringResource(Res.string.brasilia_time),
            ),
        style = MaterialTheme.typography.bodySmall.numeric(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuoteTiles(quote: Quote) {
    val extended = LocalExtendedColors.current
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val tile = Modifier.weight(1f).widthIn(min = TileMinWidth)
        QuoteTile(
            label = stringResource(Res.string.quote_buy),
            value = quote.buyPrice.format(),
            icon = AppIcons.ArrowUp,
            wellColor = extended.hero,
            iconColor = extended.onHero,
            modifier = tile,
        )
        QuoteTile(
            label = stringResource(Res.string.quote_sell),
            value = quote.sellPrice.format(),
            icon = AppIcons.ArrowDown,
            wellColor = extended.tint,
            iconColor = MaterialTheme.colorScheme.onSurface,
            modifier = tile,
        )
    }
}

/** One price; read as one text, "Cotação de compra: R$ 400.000,00". */
@Composable
private fun QuoteTile(
    label: String,
    value: String,
    icon: ImageVector,
    wellColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier.clearAndSetSemantics { text = AnnotatedString("$label: $value") }) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(IconWellSize).background(wellColor, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconColor)
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(value, style = MaterialTheme.typography.titleLarge.numeric())
            }
        }
    }
}
