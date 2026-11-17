package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.model.Quote
import com.kotlinbitcoins.web.state.shared.QuoteState
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.brasilia_time
import com.kotlinbitcoins.web.ui.resources.quote_at
import com.kotlinbitcoins.web.ui.resources.quote_buy
import com.kotlinbitcoins.web.ui.resources.quote_sell
import com.kotlinbitcoins.web.ui.resources.quote_title
import com.kotlinbitcoins.web.ui.resources.quote_unavailable
import com.kotlinbitcoins.web.ui.shared.message
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.stringResource

/**
 * Current quote (FR-013): buy, sell and the instant with seconds in Brasília. "Unavailable" shows only the message,
 * never old values (US2-3). No live region: periodic updates are not announced (FR-047); the transitions are
 * announced by QuoteAvailabilityTracker.
 */
@Composable
fun QuoteBlock(
    state: QuoteState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.quote_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            when (state) {
                QuoteState.Loading -> LoadingIndicator()
                is QuoteState.Available -> QuoteValues(state.quote)
                QuoteState.Unavailable -> Text(stringResource(Res.string.quote_unavailable))
                is QuoteState.Failed -> ErrorBlock(state.error.message(), onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun QuoteValues(quote: Quote) {
    val numeric = MaterialTheme.typography.bodyLarge.numeric()
    Text("${stringResource(Res.string.quote_buy)}: ${quote.buyPrice.format()}", style = numeric)
    Text("${stringResource(Res.string.quote_sell)}: ${quote.sellPrice.format()}", style = numeric)
    Text(
        text =
            stringResource(
                Res.string.quote_at,
                quote.quotedAt.formatWithSeconds(),
                stringResource(Res.string.brasilia_time),
            ),
        style = MaterialTheme.typography.bodySmall.numeric(),
    )
}
