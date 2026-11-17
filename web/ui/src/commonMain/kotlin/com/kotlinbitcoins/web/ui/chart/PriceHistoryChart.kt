package com.kotlinbitcoins.web.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.market.ChartData
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.market_buy_price
import com.kotlinbitcoins.web.ui.resources.market_legend_buy_style
import com.kotlinbitcoins.web.ui.resources.market_legend_sell_style
import com.kotlinbitcoins.web.ui.resources.market_sell_price
import com.kotlinbitcoins.web.ui.theme.LocalExtendedColors
import org.jetbrains.compose.resources.stringResource

private val ChartHeight = 260.dp
private val LineWidth = 2.dp
private val MarkerSize = 6.dp
private val AxisGap = 8.dp
private val DashOn = 6.dp
private val DashOff = 4.dp
private val SampleWidth = 32.dp
private val SampleHeight = 12.dp

/** How one series is drawn: buy is solid with circles, sell is dashed with squares (FR-032, not by color only). */
private enum class SeriesStyle { Solid, Dashed }

/**
 * Line chart of the last 24 h (W-13): buy as a solid line with circles, sell as a dashed line with squares, one
 * [Path] per segment (a gap is never joined, FR-033), time axis every 4 h in Brasília and price axis with the exact
 * marks of [ChartData]. Drawn ready, with no animation. The canvas is read as [summary] (FR-034); the legend is text.
 */
@Composable
fun PriceHistoryChart(
    data: ChartData,
    summary: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalExtendedColors.current
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = axisColor)
    val measurer = rememberTextMeasurer()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(ChartHeight)
                    .semantics { contentDescription = summary },
        ) {
            val priceLabels = data.priceMarks.map { measurer.measure(it.format(), labelStyle) }
            val timeLabels = data.timeMarks.map { measurer.measure(it.formatTime(), labelStyle) }
            val labelHeight = (priceLabels + timeLabels).maxOfOrNull { it.size.height } ?: 0
            val left = (priceLabels.maxOfOrNull { it.size.width } ?: 0) + AxisGap.toPx()
            val top = maxOf(labelHeight / 2f, MarkerSize.toPx())
            val right = (timeLabels.maxOfOrNull { it.size.width } ?: 0) / 2f
            val bottom = labelHeight + AxisGap.toPx()
            val plot = ChartGeometry(data, size.width - left - right, size.height - top - bottom)
            val origin = Offset(left, top)

            drawPriceAxis(plot, origin, priceLabels, gridColor)
            drawTimeAxis(plot, origin, timeLabels, axisColor)
            drawSeries(plot.paths(data.buy), origin, colors.chartBuy, SeriesStyle.Solid)
            drawSeries(plot.paths(data.sell), origin, colors.chartSell, SeriesStyle.Dashed)
        }
        Legend(colors.chartBuy, colors.chartSell)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(
    buyColor: Color,
    sellColor: Color,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LegendItem(
            stringResource(Res.string.market_buy_price),
            stringResource(Res.string.market_legend_buy_style),
            buyColor,
            SeriesStyle.Solid,
        )
        LegendItem(
            stringResource(Res.string.market_sell_price),
            stringResource(Res.string.market_legend_sell_style),
            sellColor,
            SeriesStyle.Dashed,
        )
    }
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
            drawSeries(line, Offset.Zero, color, seriesStyle, markersAt = listOf(middle))
        }
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun DrawScope.drawPriceAxis(
    plot: ChartGeometry,
    origin: Offset,
    labels: List<TextLayoutResult>,
    gridColor: Color,
) {
    plot.priceMarks().zip(labels).forEach { (mark, label) ->
        val y = origin.y + mark.position
        drawLine(gridColor, Offset(origin.x, y), Offset(origin.x + plot.width, y), strokeWidth = 1.dp.toPx())
        drawText(label, topLeft = Offset(origin.x - AxisGap.toPx() - label.size.width, y - label.size.height / 2f))
    }
}

private fun DrawScope.drawTimeAxis(
    plot: ChartGeometry,
    origin: Offset,
    labels: List<TextLayoutResult>,
    axisColor: Color,
) {
    val baseline = origin.y + plot.height
    drawLine(axisColor, Offset(origin.x, baseline), Offset(origin.x + plot.width, baseline), 1.dp.toPx())
    plot.timeMarks().zip(labels).forEach { (mark, label) ->
        val x = origin.x + mark.position
        drawLine(axisColor, Offset(x, baseline), Offset(x, baseline + AxisGap.toPx() / 2), 1.dp.toPx())
        drawText(label, topLeft = Offset(x - label.size.width / 2f, baseline + AxisGap.toPx()))
    }
}

/** One path per segment and one marker per point; a segment of one point is only its marker. */
private fun DrawScope.drawSeries(
    segments: List<List<Offset>>,
    origin: Offset,
    color: Color,
    style: SeriesStyle,
    markersAt: List<Offset>? = null,
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
            val at = origin + point
            if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
        }
        drawPath(path, color, style = stroke)
    }
    (markersAt ?: segments.flatten()).forEach { drawMarker(origin + it, color, style) }
}

private fun DrawScope.drawMarker(
    center: Offset,
    color: Color,
    style: SeriesStyle,
) {
    val side = MarkerSize.toPx()
    when (style) {
        SeriesStyle.Solid -> drawCircle(color, radius = side / 2, center = center)
        SeriesStyle.Dashed -> drawRect(color, topLeft = center - Offset(side / 2, side / 2), size = Size(side, side))
    }
}
