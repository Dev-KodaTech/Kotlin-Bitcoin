package com.kotlinbitcoins.web.ui.chart

import androidx.compose.runtime.Composable
import com.kotlinbitcoins.web.core.market.ChartData
import com.kotlinbitcoins.web.core.market.SeriesSummary
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.market_buy_price
import com.kotlinbitcoins.web.ui.resources.market_sell_price
import com.kotlinbitcoins.web.ui.resources.market_series_summary
import com.kotlinbitcoins.web.ui.resources.market_summary
import org.jetbrains.compose.resources.stringResource

/**
 * Textual alternative of the price chart (FR-034), used wherever the chart is drawn:
 * "Gráfico … Preço de compra: mínimo R$ …, máximo R$ …, último R$ … às 08:10. Preço de venda: …".
 */
@Composable
fun chartSummary(chart: ChartData): String =
    stringResource(
        Res.string.market_summary,
        seriesSummary(stringResource(Res.string.market_buy_price), chart.summary.buy),
        seriesSummary(stringResource(Res.string.market_sell_price), chart.summary.sell),
    )

@Composable
private fun seriesSummary(
    name: String,
    summary: SeriesSummary,
): String =
    stringResource(
        Res.string.market_series_summary,
        name,
        summary.min.format(),
        summary.max.format(),
        summary.last.price.format(),
        summary.last.at.formatTime(),
    )
