package com.kotlinbitcoins.web.ui.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.market.ChartData
import com.kotlinbitcoins.web.core.market.SeriesSummary
import com.kotlinbitcoins.web.core.market.TableRow
import com.kotlinbitcoins.web.core.model.DailyVolume
import com.kotlinbitcoins.web.state.market.MarketUiState
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.ui.chart.PriceHistoryChart
import com.kotlinbitcoins.web.ui.components.ErrorBlock
import com.kotlinbitcoins.web.ui.components.LoadingIndicator
import com.kotlinbitcoins.web.ui.components.MinTouchTarget
import com.kotlinbitcoins.web.ui.components.PageHeading
import com.kotlinbitcoins.web.ui.components.focusOutline
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.market_bought
import com.kotlinbitcoins.web.ui.resources.market_buy_price
import com.kotlinbitcoins.web.ui.resources.market_chart_title
import com.kotlinbitcoins.web.ui.resources.market_empty
import com.kotlinbitcoins.web.ui.resources.market_hide_table
import com.kotlinbitcoins.web.ui.resources.market_no_record
import com.kotlinbitcoins.web.ui.resources.market_refresh
import com.kotlinbitcoins.web.ui.resources.market_refreshing
import com.kotlinbitcoins.web.ui.resources.market_row_description
import com.kotlinbitcoins.web.ui.resources.market_row_prices
import com.kotlinbitcoins.web.ui.resources.market_sell_price
import com.kotlinbitcoins.web.ui.resources.market_series_summary
import com.kotlinbitcoins.web.ui.resources.market_show_table
import com.kotlinbitcoins.web.ui.resources.market_sold
import com.kotlinbitcoins.web.ui.resources.market_summary
import com.kotlinbitcoins.web.ui.resources.market_table_time
import com.kotlinbitcoins.web.ui.resources.market_volume_title
import com.kotlinbitcoins.web.ui.resources.page_market
import com.kotlinbitcoins.web.ui.shared.message
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.stringResource

private val FieldMinWidth = 160.dp
private val TimeColumnWidth = 150.dp

/**
 * Mercado (US8): h1, "Atualizar" (FR-035), the volume of the day with its date (FR-031) and the 24 h chart with its
 * legend, its textual summary (FR-034) and the "Ver dados em tabela" disclosure, whose rows show every 10 min slot,
 * the missing ones as "sem registro". The two blocks load, fail and are empty independently (US8-5). Stateless.
 */
@Composable
fun MarketScreen(
    state: MarketUiState,
    isTableVisible: Boolean,
    actions: MarketActions,
) {
    val chart = (state.history as? Loadable.Loaded<ChartData>)?.value
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PageHeading(stringResource(Res.string.page_market)) }
        item { RefreshRow(state.isRefreshing, actions.onRefresh) }
        item { VolumeBlock(state.volume, actions.onRetryVolume) }
        item { ChartBlock(state.history, isTableVisible, actions) }
        if (chart != null && isTableVisible) tableRows(chart)
    }
}

@Composable
private fun RefreshRow(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onRefresh, modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline()) {
            Text(stringResource(Res.string.market_refresh))
        }
        if (isRefreshing) {
            Text(
                stringResource(Res.string.market_refreshing),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VolumeBlock(
    volume: Loadable<DailyVolume>,
    onRetry: () -> Unit,
) {
    when (volume) {
        Loadable.Loading -> {
            LoadingIndicator()
        }

        Loadable.Empty -> {}

        is Loadable.Failed -> {
            ErrorBlock(volume.error.message(), onRetry = onRetry)
        }

        is Loadable.Loaded -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(stringResource(Res.string.market_volume_title, volume.value.date.toUserString()))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LabeledValue(stringResource(Res.string.market_bought), volume.value.boughtBtc.format())
                        LabeledValue(stringResource(Res.string.market_sold), volume.value.soldBtc.format())
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartBlock(
    history: Loadable<ChartData>,
    isTableVisible: Boolean,
    actions: MarketActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(Res.string.market_chart_title))
        when (history) {
            Loadable.Loading -> {
                LoadingIndicator()
            }

            Loadable.Empty -> {
                Text(stringResource(Res.string.market_empty), style = MaterialTheme.typography.bodyLarge)
            }

            is Loadable.Failed -> {
                ErrorBlock(history.error.message(), onRetry = actions.onRetryHistory)
            }

            is Loadable.Loaded -> {
                PriceHistoryChart(history.value, chartSummary(history.value), modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = actions.onToggleTable,
                    modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline(),
                ) {
                    Text(
                        stringResource(
                            if (isTableVisible) Res.string.market_hide_table else Res.string.market_show_table,
                        ),
                    )
                }
            }
        }
    }
}

/** "Gráfico … Preço de compra: mínimo R$ …, máximo R$ …, último R$ … às 08:10. Preço de venda: …" (FR-034). */
@Composable
private fun chartSummary(chart: ChartData): String =
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

/** Header and one row per slot; each row is read at once ("09/11/2026 02:00: sem registro"). */
private fun LazyListScope.tableRows(chart: ChartData) {
    item { TableHeader() }
    items(chart.rows, key = ::rowKey) { row -> TableRowLine(row) }
}

@Composable
private fun TableHeader() {
    Column {
        Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            HeaderCell(stringResource(Res.string.market_table_time), Modifier.widthIn(min = TimeColumnWidth))
            HeaderCell(stringResource(Res.string.market_buy_price), Modifier.weight(1f))
            HeaderCell(stringResource(Res.string.market_sell_price), Modifier.weight(1f))
        }
        HorizontalDivider()
    }
}

@Composable
private fun HeaderCell(
    text: String,
    modifier: Modifier,
) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = modifier.padding(vertical = 4.dp))
}

@Composable
private fun TableRowLine(row: TableRow) {
    val time =
        when (row) {
            is TableRow.Recorded -> row.point.slotAt
            is TableRow.Missing -> row.slotAt
        }.format()
    val values =
        when (row) {
            is TableRow.Recorded -> {
                stringResource(Res.string.market_row_prices, row.point.buyPrice.format(), row.point.sellPrice.format())
            }

            is TableRow.Missing -> {
                stringResource(Res.string.market_no_record)
            }
        }
    val style = MaterialTheme.typography.bodyMedium.numeric()
    val description = stringResource(Res.string.market_row_description, time, values)
    Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Text(time, style = style, modifier = Modifier.widthIn(min = TimeColumnWidth))
        when (row) {
            is TableRow.Recorded -> {
                Text(row.point.buyPrice.format(), style = style, modifier = Modifier.weight(1f))
                Text(row.point.sellPrice.format(), style = style, modifier = Modifier.weight(1f))
            }

            is TableRow.Missing -> {
                Text(values, style = style, modifier = Modifier.weight(2f))
            }
        }
    }
}

private fun rowKey(row: TableRow): String =
    when (row) {
        is TableRow.Recorded -> {
            row.point.slotAt.instant
                .toString()
        }

        is TableRow.Missing -> {
            row.slotAt.instant.toString()
        }
    }

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun LabeledValue(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.widthIn(min = FieldMinWidth)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.titleLarge.numeric())
    }
}
