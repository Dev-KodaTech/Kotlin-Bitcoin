package com.kotlinbitcoins.web.ui.investments

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.model.InvestmentPosition
import com.kotlinbitcoins.web.core.model.Position
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.investments.InvestmentsUiState
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.ui.components.ErrorBlock
import com.kotlinbitcoins.web.ui.components.LoadingIndicator
import com.kotlinbitcoins.web.ui.components.MinTouchTarget
import com.kotlinbitcoins.web.ui.components.PageHeading
import com.kotlinbitcoins.web.ui.components.PauseToggle
import com.kotlinbitcoins.web.ui.components.VariationBadge
import com.kotlinbitcoins.web.ui.components.focusOutline
import com.kotlinbitcoins.web.ui.components.spokenText
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.brasilia_time
import com.kotlinbitcoins.web.ui.resources.investments_buy
import com.kotlinbitcoins.web.ui.resources.investments_card_description
import com.kotlinbitcoins.web.ui.resources.investments_current_value
import com.kotlinbitcoins.web.ui.resources.investments_empty
import com.kotlinbitcoins.web.ui.resources.investments_invested
import com.kotlinbitcoins.web.ui.resources.investments_list
import com.kotlinbitcoins.web.ui.resources.investments_purchase_quote
import com.kotlinbitcoins.web.ui.resources.investments_purchased_at
import com.kotlinbitcoins.web.ui.resources.investments_reference_quote
import com.kotlinbitcoins.web.ui.resources.investments_total
import com.kotlinbitcoins.web.ui.resources.investments_variation
import com.kotlinbitcoins.web.ui.resources.page_investments
import com.kotlinbitcoins.web.ui.resources.quote_at
import com.kotlinbitcoins.web.ui.resources.quote_unavailable
import com.kotlinbitcoins.web.ui.shared.message
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.stringResource

private val FieldMinWidth = 140.dp

/**
 * Meus investimentos (US5): h1, the pause toggle, the total and the reference quote with its instant (US5-3), and one
 * card per open investment in the API order (FR-019). Each card is focusable and read at once as a full sentence
 * (W-14, variant (b) of "W-00 Resultado", also on wide screens, where the fields line up side by side). Empty state
 * with a shortcut to buy (FR-021); unavailable quote with "Tentar de novo" and no old values (US5-5). The structure
 * does not change between refreshes, so the focus stays where it was (US5-6). Stateless.
 */
@Composable
fun InvestmentsScreen(
    state: InvestmentsUiState,
    actions: InvestmentsActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PageHeading(stringResource(Res.string.page_investments)) }
        item { PauseToggle(isPaused = state.isAutoRefreshPaused, onToggle = actions.onToggleAutoRefresh) }
        positionContent(state.position, actions)
    }
}

private fun LazyListScope.positionContent(
    position: Loadable<Position>,
    actions: InvestmentsActions,
) {
    when (position) {
        Loadable.Loading -> {
            item { LoadingIndicator() }
        }

        Loadable.Empty -> {
            item { EmptyState(onBuy = { actions.onNavigate(AppRoute.Purchase) }) }
        }

        is Loadable.Failed -> {
            item { FailedState(position.error, actions.onRetry) }
        }

        is Loadable.Loaded -> {
            item { Summary(position.value) }
            item {
                Text(
                    text = stringResource(Res.string.investments_list),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(position.value.investments, key = { it.id }) { InvestmentCard(it) }
        }
    }
}

@Composable
private fun Summary(position: Position) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.investments_total),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(position.totalCurrentValue.format(), style = MaterialTheme.typography.headlineSmall.numeric())
            Text(
                text =
                    "${stringResource(Res.string.investments_reference_quote)}: ${position.quote.buyPrice.format()}",
                style = MaterialTheme.typography.bodyLarge.numeric(),
            )
            Text(
                text =
                    stringResource(
                        Res.string.quote_at,
                        position.quote.quotedAt.formatWithSeconds(),
                        stringResource(Res.string.brasilia_time),
                    ),
                style = MaterialTheme.typography.bodySmall.numeric(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InvestmentCard(investment: InvestmentPosition) {
    val brasilia = stringResource(Res.string.brasilia_time)
    val description =
        stringResource(
            Res.string.investments_card_description,
            investment.purchasedAt.format(),
            brasilia,
            investment.investedAmount.format(),
            investment.purchaseQuote.format(),
            investment.variationPercent.spokenText(),
            investment.currentValue.format(),
        )
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusOutline()
                .focusable()
                .clearAndSetSemantics { contentDescription = description },
    ) {
        FlowRow(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Field(stringResource(Res.string.investments_purchased_at)) {
                Text("${investment.purchasedAt.format()} $brasilia", style = valueStyle())
            }
            Field(stringResource(Res.string.investments_invested)) {
                Text(investment.investedAmount.format(), style = valueStyle())
            }
            Field(stringResource(Res.string.investments_purchase_quote)) {
                Text(investment.purchaseQuote.format(), style = valueStyle())
            }
            Field(stringResource(Res.string.investments_variation)) {
                VariationBadge(investment.variationPercent)
            }
            Field(stringResource(Res.string.investments_current_value)) {
                Text(investment.currentValue.format(), style = valueStyle())
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: @Composable () -> Unit,
) {
    Column(modifier = Modifier.widthIn(min = FieldMinWidth)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        value()
    }
}

@Composable
private fun valueStyle() = MaterialTheme.typography.bodyLarge.numeric()

@Composable
private fun EmptyState(onBuy: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.investments_empty), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onBuy, modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline()) {
            Text(stringResource(Res.string.investments_buy))
        }
    }
}

/** The unavailable quote has its own message (no generic error, FR-013); any other failure its pt-BR message. */
@Composable
private fun FailedState(
    error: ApiError,
    onRetry: () -> Unit,
) {
    val message =
        if (error == ApiError.QuoteUnavailable) stringResource(Res.string.quote_unavailable) else error.message()
    ErrorBlock(message, onRetry = onRetry)
}
