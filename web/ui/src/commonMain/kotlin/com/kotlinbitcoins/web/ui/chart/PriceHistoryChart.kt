package com.kotlinbitcoins.web.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.market.ChartData
import com.kotlinbitcoins.web.core.market.SeriesSummary
import com.kotlinbitcoins.web.ui.components.VariationBadge
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.chart_change_24h
import com.kotlinbitcoins.web.ui.resources.chart_coverage
import com.kotlinbitcoins.web.ui.resources.chart_coverage_value
import com.kotlinbitcoins.web.ui.resources.chart_flat_note
import com.kotlinbitcoins.web.ui.resources.chart_gap_legend
import com.kotlinbitcoins.web.ui.resources.chart_gaps_many
import com.kotlinbitcoins.web.ui.resources.chart_gaps_none
import com.kotlinbitcoins.web.ui.resources.chart_gaps_one
import com.kotlinbitcoins.web.ui.resources.chart_spread
import com.kotlinbitcoins.web.ui.resources.chart_spread_hint
import com.kotlinbitcoins.web.ui.resources.market_buy_price
import com.kotlinbitcoins.web.ui.resources.market_legend_buy_style
import com.kotlinbitcoins.web.ui.resources.market_legend_sell_style
import com.kotlinbitcoins.web.ui.resources.market_sell_price
import com.kotlinbitcoins.web.ui.theme.LocalExtendedColors
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.stringResource

private val LineWidth = 2.dp
private val MarkerRing = 2.dp
private val LegendMarker = 7.dp
private val DashOn = 6.dp
private val DashOff = 4.dp
private val SampleWidth = 24.dp
private val SampleHeight = 10.dp
private val SwatchWidth = 16.dp
private val SwatchHeight = 12.dp
private val FiguresShape = RoundedCornerShape(16.dp)
private val FigurePaddingX = 16.dp
private val FigurePaddingY = 12.dp
private val NoteShape = RoundedCornerShape(14.dp)
private val NotePaddingX = 12.dp
private val NotePaddingY = 6.dp

/** Below this width the four figures are a 2 × 2 grid instead of one row. */
private val FiguresRowMinWidth = 640.dp
private const val FIGURES_PER_ROW_NARROW = 2

/** How one series is drawn: buy is solid with circles, sell is dashed with squares (FR-032, not by color only). */
internal enum class SeriesStyle { Solid, Dashed }

/**
 * Price chart of the last 24 h (W-13): a strip of figures that is also the legend (buy and sell now with their 24 h
 * change, the spread and the coverage of the window), the plot ([ChartCanvas]: a gap is never joined, FR-033) and,
 * when the window has stretches without record, the key of their hatching. Drawn ready, with no animation; the canvas
 * is read as [summary] (FR-034).
 */
@Composable
fun PriceHistoryChart(
    data: ChartData,
    summary: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ChartFigures(data)
        ChartCanvas(data, summary)
        if (data.yRange.min == data.yRange.max) FlatNote(data)
        if (data.gaps.isNotEmpty()) GapLegend()
    }
}

@Composable
private fun ChartFigures(data: ChartData) {
    val colors = chartColors()
    val border = MaterialTheme.colorScheme.outlineVariant
    val figures: List<@Composable (Modifier) -> Unit> =
        listOf(
            { SeriesFigure(SeriesStyle.Solid, colors.buy, data.summary.buy, it) },
            { SeriesFigure(SeriesStyle.Dashed, colors.sell, data.summary.sell, it) },
            {
                PlainFigure(
                    stringResource(Res.string.chart_spread),
                    data.summary.spread.format(),
                    stringResource(Res.string.chart_spread_hint),
                    it,
                )
            },
            { CoverageFigure(data, it) },
        )
    BoxWithConstraints(Modifier.fillMaxWidth().border(1.dp, border, FiguresShape)) {
        val rows = if (maxWidth >= FiguresRowMinWidth) listOf(figures) else figures.chunked(FIGURES_PER_ROW_NARROW)
        Column {
            rows.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) HorizontalDivider(color = border)
                Row(Modifier.height(IntrinsicSize.Min)) {
                    row.forEachIndexed { index, figure ->
                        if (index > 0) VerticalDivider(color = border)
                        figure(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FigureCell(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = FigurePaddingX, vertical = FigurePaddingY),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun FigureValue(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium.numeric(), fontWeight = FontWeight.SemiBold)
}

@Composable
private fun FigureHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Legend of the series, its last price and its change over the window ("↑ +0,52% alta em 24 h"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesFigure(
    style: SeriesStyle,
    color: Color,
    summary: SeriesSummary,
    modifier: Modifier,
) {
    val isBuy = style == SeriesStyle.Solid
    FigureCell(modifier) {
        LegendItem(
            stringResource(if (isBuy) Res.string.market_buy_price else Res.string.market_sell_price),
            stringResource(if (isBuy) Res.string.market_legend_buy_style else Res.string.market_legend_sell_style),
            color,
            style,
        )
        FigureValue(summary.last.price.format())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            VariationBadge(summary.change, style = MaterialTheme.typography.bodySmall)
            FigureHint(stringResource(Res.string.chart_change_24h))
        }
    }
}

@Composable
private fun PlainFigure(
    label: String,
    value: String,
    hint: String,
    modifier: Modifier,
) {
    FigureCell(modifier) {
        FigureHint(label)
        FigureValue(value)
        FigureHint(hint)
    }
}

/** "86% das 24 h" and how many stretches have no record. */
@Composable
private fun CoverageFigure(
    data: ChartData,
    modifier: Modifier,
) {
    val gaps = data.gaps.size
    PlainFigure(
        stringResource(Res.string.chart_coverage),
        stringResource(Res.string.chart_coverage_value, "${data.coverage.percent}%"),
        when (gaps) {
            0 -> stringResource(Res.string.chart_gaps_none)
            1 -> stringResource(Res.string.chart_gaps_one)
            else -> stringResource(Res.string.chart_gaps_many, gaps.toString())
        },
        modifier,
    )
}

/** A sample of the line and marker (decorative) and the series name; read as "Preço de compra, linha contínua…". */
@Composable
private fun LegendItem(
    name: String,
    styleDescription: String,
    color: Color,
    seriesStyle: SeriesStyle,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$name, $styleDescription" },
    ) {
        Canvas(modifier = Modifier.size(SampleWidth, SampleHeight)) {
            val middle = Offset(size.width / 2, size.height / 2)
            val line = listOf(listOf(Offset(0f, middle.y), Offset(size.width, middle.y)))
            drawSeries(line, color, seriesStyle, markersAt = listOf(middle), markerSide = LegendMarker.toPx())
        }
        FigureHint(name)
    }
}

/** Every price of the window is the same: said in words, since the line alone looks like missing data. */
@Composable
private fun FlatNote(data: ChartData) {
    Surface(color = LocalExtendedColors.current.tint, shape = NoteShape) {
        Text(
            stringResource(Res.string.chart_flat_note, data.yRange.min.format()),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = NotePaddingX, vertical = NotePaddingY),
        )
    }
}

@Composable
private fun GapLegend() {
    val hatch = chartColors().hatch
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(SwatchWidth, SwatchHeight)) {
            drawHatch(0f, size.width, size.height, hatch)
        }
        FigureHint(stringResource(Res.string.chart_gap_legend))
    }
}

/**
 * One path per segment; a marker only where a segment is a single point (it would not show otherwise) or at
 * [markersAt]. The last point and the pointer get their markers from [ChartCanvas].
 */
internal fun DrawScope.drawSeries(
    segments: List<List<Offset>>,
    color: Color,
    style: SeriesStyle,
    markersAt: List<Offset>? = null,
    markerSide: Float = LegendMarker.toPx(),
) {
    val stroke =
        Stroke(
            width = LineWidth.toPx(),
            pathEffect =
                if (style == SeriesStyle.Dashed) {
                    PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx()))
                } else {
                    null
                },
        )
    segments.filter { it.size > 1 }.forEach { segment ->
        val path = Path()
        segment.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, color, style = stroke)
    }
    (markersAt ?: segments.filter { it.size == 1 }.flatten()).forEach { drawMarker(it, color, style, markerSide) }
}

/** Circle for buy, square for sell; with a [ring] of the background color when drawn over the lines. */
internal fun DrawScope.drawMarker(
    center: Offset,
    color: Color,
    style: SeriesStyle,
    side: Float,
    ring: Color? = null,
) {
    ring?.let { drawMarkerShape(center, it, style, side + MarkerRing.toPx() * 2) }
    drawMarkerShape(center, color, style, side)
}

private fun DrawScope.drawMarkerShape(
    center: Offset,
    color: Color,
    style: SeriesStyle,
    side: Float,
) {
    when (style) {
        SeriesStyle.Solid -> drawCircle(color, radius = side / 2, center = center)
        SeriesStyle.Dashed -> drawRect(color, topLeft = center - Offset(side / 2, side / 2), size = Size(side, side))
    }
}
