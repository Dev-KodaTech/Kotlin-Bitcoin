package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.brasilia_time
import com.kotlinbitcoins.web.ui.resources.estimate_quote_at
import com.kotlinbitcoins.web.ui.resources.estimate_value
import com.kotlinbitcoins.web.ui.resources.quote_unavailable
import com.kotlinbitcoins.web.ui.shared.message
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.stringResource

/**
 * Labeled estimate of a purchase or redemption (FR-015, FR-023): "Estimativa: X BTC", the quote and the instant used,
 * the [notice] that the final amount uses the quote of the operation and any warning. No live region: a new quote
 * changes the values without being announced (FR-047). [priceLabel] names the quote used ("Cotação de venda usada").
 */
@Composable
fun EstimateCard(
    display: EstimateDisplay,
    priceLabel: String,
    notice: String,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (display) {
                EstimateDisplay.Loading -> {
                    LoadingIndicator()
                }

                EstimateDisplay.QuoteUnavailable -> {
                    Text(stringResource(Res.string.quote_unavailable), style = MaterialTheme.typography.bodyLarge)
                }

                is EstimateDisplay.Failed -> {
                    ErrorBlock(display.error.message())
                }

                is EstimateDisplay.Ready -> {
                    EstimateValues(display, priceLabel)
                }
            }
            Text(notice, style = MaterialTheme.typography.bodyMedium)
            if (display is EstimateDisplay.Ready) {
                display.warnings.forEach { warning ->
                    Text(warning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun EstimateValues(
    estimate: EstimateDisplay.Ready,
    priceLabel: String,
) {
    Text(
        text = stringResource(Res.string.estimate_value, estimate.btc.format()),
        style = MaterialTheme.typography.titleLarge.numeric(),
    )
    Text("$priceLabel: ${estimate.price.format()}", style = MaterialTheme.typography.bodyLarge.numeric())
    Text(
        text =
            stringResource(
                Res.string.estimate_quote_at,
                estimate.quotedAt.formatWithSeconds(),
                stringResource(Res.string.brasilia_time),
            ),
        style = MaterialTheme.typography.bodySmall.numeric(),
    )
}
