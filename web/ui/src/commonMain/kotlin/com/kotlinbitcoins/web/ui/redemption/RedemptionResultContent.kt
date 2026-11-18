package com.kotlinbitcoins.web.ui.redemption

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.model.Liquidation
import com.kotlinbitcoins.web.core.model.RedemptionResult
import com.kotlinbitcoins.web.core.model.Reinvestment
import com.kotlinbitcoins.web.ui.components.AppCard
import com.kotlinbitcoins.web.ui.components.CardPadding
import com.kotlinbitcoins.web.ui.components.FocusOnArrival
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.brasilia_time
import com.kotlinbitcoins.web.ui.resources.redemption_btc_sold
import com.kotlinbitcoins.web.ui.resources.redemption_liquidation_description
import com.kotlinbitcoins.web.ui.resources.redemption_liquidation_partial
import com.kotlinbitcoins.web.ui.resources.redemption_liquidation_total
import com.kotlinbitcoins.web.ui.resources.redemption_liquidations
import com.kotlinbitcoins.web.ui.resources.redemption_new_balance
import com.kotlinbitcoins.web.ui.resources.redemption_occurred_at
import com.kotlinbitcoins.web.ui.resources.redemption_quote_used
import com.kotlinbitcoins.web.ui.resources.redemption_redeemed
import com.kotlinbitcoins.web.ui.resources.redemption_reinvestment
import com.kotlinbitcoins.web.ui.resources.redemption_reinvestment_btc
import com.kotlinbitcoins.web.ui.resources.redemption_reinvestment_explanation
import com.kotlinbitcoins.web.ui.resources.redemption_reinvestment_invested
import com.kotlinbitcoins.web.ui.resources.redemption_reinvestment_purchased_at
import com.kotlinbitcoins.web.ui.resources.redemption_reinvestment_quote
import com.kotlinbitcoins.web.ui.resources.redemption_result_title
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.stringResource

/**
 * Result of a redemption (US6-3/US6-4, FR-024), only with the values of the API: the summary, the liquidated
 * investments in the API order with "Total"/"Parcial" in text (not only by color) and, only when there is one, the
 * reinvestment with a short explanation. The title takes the focus on arrival (FR-046).
 */
@Composable
fun RedemptionResultContent(
    result: RedemptionResult,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusOnArrival(enabled = true) { arrivalModifier ->
                Text(
                    text = stringResource(Res.string.redemption_result_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }.then(arrivalModifier),
                )
            }
            ResultLine(stringResource(Res.string.redemption_redeemed), result.amount.format())
            ResultLine(stringResource(Res.string.redemption_btc_sold), result.btcSold.format())
            ResultLine(stringResource(Res.string.redemption_quote_used), result.quote.format())
            ResultLine(
                label = stringResource(Res.string.redemption_occurred_at),
                value = result.occurredAt.format() + " " + stringResource(Res.string.brasilia_time),
            )
            ResultLine(stringResource(Res.string.redemption_new_balance), result.balance.format())
        }
    }
    Liquidations(result.liquidations)
    result.reinvestment?.let { ReinvestmentSection(it) }
}

@Composable
private fun Liquidations(liquidations: List<Liquidation>) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.redemption_liquidations),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            liquidations.forEachIndexed { index, liquidation ->
                if (index > 0) HorizontalDivider()
                LiquidationRow(liquidation)
            }
        }
    }
}

@Composable
private fun LiquidationRow(liquidation: Liquidation) {
    val kind =
        if (liquidation.isPartial) {
            stringResource(Res.string.redemption_liquidation_partial)
        } else {
            stringResource(Res.string.redemption_liquidation_total)
        }
    val description =
        stringResource(
            Res.string.redemption_liquidation_description,
            kind,
            liquidation.amount.format(),
            liquidation.btcAmount.format(),
        )
    // Read at once, like a card of "Meus investimentos" (W-14).
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(kind, style = MaterialTheme.typography.labelLarge)
        Text(liquidation.amount.format(), style = MaterialTheme.typography.bodyLarge.numeric())
        Text(liquidation.btcAmount.format(), style = MaterialTheme.typography.bodyMedium.numeric())
    }
}

@Composable
private fun ReinvestmentSection(reinvestment: Reinvestment) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.redemption_reinvestment),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.redemption_reinvestment_explanation),
                style = MaterialTheme.typography.bodyMedium,
            )
            ResultLine(stringResource(Res.string.redemption_reinvestment_btc), reinvestment.btcAmount.format())
            ResultLine(
                stringResource(Res.string.redemption_reinvestment_invested),
                reinvestment.investedAmount.format(),
            )
            ResultLine(stringResource(Res.string.redemption_reinvestment_quote), reinvestment.purchaseQuote.format())
            ResultLine(
                label = stringResource(Res.string.redemption_reinvestment_purchased_at),
                value = reinvestment.purchasedAt.format() + " " + stringResource(Res.string.brasilia_time),
            )
        }
    }
}

@Composable
internal fun ResultLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(value, style = MaterialTheme.typography.headlineSmall.numeric())
    }
}
