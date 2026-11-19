package com.kotlinbitcoins.web.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.market.ChartData
import com.kotlinbitcoins.web.core.market.ChartGap
import com.kotlinbitcoins.web.core.market.TableRow
import com.kotlinbitcoins.web.core.model.HistoryPoint
import com.kotlinbitcoins.web.core.money.PriceDifference
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.time.ApiInstant
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.chart_buy
import com.kotlinbitcoins.web.ui.resources.chart_equal_prices
import com.kotlinbitcoins.web.ui.resources.chart_no_record
import com.kotlinbitcoins.web.ui.resources.chart_no_record_here
import com.kotlinbitcoins.web.ui.resources.chart_now
import com.kotlinbitcoins.web.ui.resources.chart_sell
import com.kotlinbitcoins.web.ui.resources.chart_spread_short
import com.kotlinbitcoins.web.ui.theme.ExtendedColors
import com.kotlinbitcoins.web.ui.theme.LocalExtendedColors
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private val CanvasHeight = 280.dp
private val AxisGap = 8.dp
private val PointerSlack = 8.dp
private val GridDash = 3.dp
private val CrossDash = 3.dp
private val HatchStep = 7.dp
private val HatchWidth = 1.5.dp
private val GapLabelTop = 6.dp
private val GapLabelPadding = 4.dp
private val LastMarker = 9.dp
private val HoverMarker = 10.dp
private val TagPaddingX = 6.dp
private val TagPaddingY = 3.dp
private val TagCorner = 6.dp
private val TagBorder = 1.5.dp
private val TagSpacing = 2.dp
private val TooltipOffset = 12.dp
private val TooltipTop = 4.dp
private val TooltipPadding = 10.dp
private val TooltipCorner = 12.dp
private val TooltipRowGap = 6.dp
private val TooltipValueGap = 16.dp
private val TooltipSwatch = 8.dp
private val TooltipSwatchGap = 6.dp
private const val AREA_ALPHA = 0.12f
private const val HATCH_ALPHA = 0.22f

/** The theme colors of the chart; every text pair is checked by ThemeContrastTest. */
internal class ChartColors(
    scheme: ColorScheme,
    extended: ExtendedColors,
) {
    val buy = extended.chartBuy
    val sell = extended.chartSell
    val area = extended.chartBuy.copy(alpha = AREA_ALPHA)
    val grid = scheme.outlineVariant
    val axis = scheme.outline
    val muted = scheme.onSurfaceVariant
    val text = scheme.onSurface
    val card = extended.card
    val hatch = scheme.onSurfaceVariant.copy(alpha = HATCH_ALPHA)
    val tooltip = scheme.surface
    val tooltipBorder = scheme.outlineVariant
}

@Composable
internal fun chartColors(): ChartColors = ChartColors(MaterialTheme.colorScheme, LocalExtendedColors.current)

/** The names of the rows of the hover box. */
private class TooltipLabels(
    val buy: String,
    val sell: String,
    val spread: String,
)

private class ChartTexts(
    val now: String,
    val noRecord: String,
    val noRecordHere: String,
    val equalPrices: String,
    val tooltip: TooltipLabels,
)

private class ChartStyles(
    val label: TextStyle,
    val labelStrong: TextStyle,
    val labelMutedStrong: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
)

/** What the canvas needs to measure and paint its texts. */
private class ChartPaint(
    val measurer: TextMeasurer,
    val colors: ChartColors,
    val styles: ChartStyles,
    val texts: ChartTexts,
) {
    fun measure(
        text: String,
        style: TextStyle,
    ): TextLayoutResult = measurer.measure(text, style)
}

/** The slot under the pointer and its record; [point] is null in a slot without record. */
private class HoverPoint(
    val at: ApiInstant,
    val point: HistoryPoint?,
)

/** The value tag at the right end of a series; one tag when both series end at the same price. */
private class EndTag(
    val layout: TextLayoutResult,
    val color: Color,
    val price: QuotePrice,
)

private class TooltipRow(
    val label: TextLayoutResult,
    val value: TextLayoutResult,
    val marker: Pair<Color, SeriesStyle>?,
    val dividerAbove: Boolean,
)

/** Where the plot was last drawn, so that a pointer position becomes a slot. Written while drawing, not a state. */
private class PlotFrame {
    private var geometry: ChartGeometry? = null
    private var origin = Offset.Zero
    private var slack = 0f

    fun update(
        geometry: ChartGeometry,
        origin: Offset,
        slack: Float,
    ) {
        this.geometry = geometry
        this.origin = origin
        this.slack = slack
    }

    fun slotAt(position: Offset): ApiInstant? {
        val plot = geometry ?: return null
        val x = position.x - origin.x
        val y = position.y - origin.y
        val inside = x >= -slack && x <= plot.width + slack && y >= 0f && y <= plot.height
        return if (inside) plot.slotAt(x) else null
    }
}

/**
 * The plot of the price chart (W-13): hatched stretches without record (FR-033), round price marks with a dashed
 * grid, time marks every 4 h ending at "Agora", the buy area and line (solid) and the sell line (dashed), the markers
 * and value tags of the last point and, under the pointer, a crosshair with both prices and the spread. Read as
 * [summary] (FR-034).
 */
@Composable
internal fun ChartCanvas(
    data: ChartData,
    summary: String,
    modifier: Modifier = Modifier,
) {
    val paint = rememberChartPaint()
    val recorded =
        remember(data) {
            data.rows.filterIsInstance<TableRow.Recorded>().associate { it.point.slotAt to it.point }
        }
    val frame = remember { PlotFrame() }
    var hoverAt by remember(data) { mutableStateOf<ApiInstant?>(null) }
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(CanvasHeight)
                .semantics { contentDescription = summary }
                .pointerInput(data) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            hoverAt =
                                if (event.type == PointerEventType.Exit) {
                                    null
                                } else {
                                    event.changes.firstOrNull()?.let { frame.slotAt(it.position) }
                                }
                        }
                    }
                },
    ) {
        drawPriceChart(data, paint, frame, hoverAt?.let { HoverPoint(it, recorded[it]) })
    }
}

@Composable
private fun rememberChartPaint(): ChartPaint {
    val colors = chartColors()
    val label = MaterialTheme.typography.labelSmall.numeric()
    val body = MaterialTheme.typography.bodySmall
    val styles =
        ChartStyles(
            label = label.copy(color = colors.muted),
            labelStrong = label.copy(color = colors.text, fontWeight = FontWeight.SemiBold),
            labelMutedStrong = label.copy(color = colors.muted, fontWeight = FontWeight.SemiBold),
            body = body.copy(color = colors.muted),
            bodyStrong = body.numeric().copy(color = colors.text, fontWeight = FontWeight.SemiBold),
        )
    val texts =
        ChartTexts(
            now = stringResource(Res.string.chart_now),
            noRecord = stringResource(Res.string.chart_no_record),
            noRecordHere = stringResource(Res.string.chart_no_record_here),
            equalPrices = stringResource(Res.string.chart_equal_prices),
            tooltip =
                TooltipLabels(
                    buy = stringResource(Res.string.chart_buy),
                    sell = stringResource(Res.string.chart_sell),
                    spread = stringResource(Res.string.chart_spread_short),
                ),
        )
    return ChartPaint(rememberTextMeasurer(), colors, styles, texts)
}

private fun DrawScope.drawPriceChart(
    data: ChartData,
    paint: ChartPaint,
    frame: PlotFrame,
    hover: HoverPoint?,
) {
    val priceLabels = data.priceMarks.map { paint.measure(it.formatAxis(), paint.styles.label) }
    val tags = endTags(data, paint)
    val caption = if (tags.size == 1) paint.measure(paint.texts.equalPrices, paint.styles.label) else null
    val now = paint.measure(paint.texts.now, paint.styles.labelStrong)
    val gap = AxisGap.toPx()
    val tagsWidth = (tags.maxOfOrNull { it.layout.size.width } ?: 0) + TagPaddingX.toPx() * 2
    val left = (priceLabels.maxOfOrNull { it.size.width } ?: 0) + gap
    val right = maxOf(maxOf(tagsWidth, (caption?.size?.width ?: 0) + 0f) + gap, now.size.width / 2f)
    val top = LastMarker.toPx()
    val bottom = gap + now.size.height * 2
    val plot = ChartGeometry(data, size.width - left - right, size.height - top - bottom)
    frame.update(plot, Offset(left, top), PointerSlack.toPx())

    translate(left = left, top = top) {
        drawGaps(data.gaps, plot, paint)
        drawPriceAxis(plot, priceLabels, paint.colors.grid)
        drawTimeAxis(data, plot, now, paint)
        drawArea(plot.paths(data.buy), plot.height, paint.colors.area)
        drawSeries(plot.paths(data.buy), paint.colors.buy, SeriesStyle.Solid)
        drawSeries(plot.paths(data.sell), paint.colors.sell, SeriesStyle.Dashed)
        drawLastPoint(data, plot, paint.colors)
        drawTags(tags, caption, plot, paint.colors.card)
        hover?.let { drawHover(it, plot, paint) }
    }
}

/** Each stretch without record is hatched, with "Sem registro" and its slots on top when they fit. */
private fun DrawScope.drawGaps(
    gaps: List<ChartGap>,
    plot: ChartGeometry,
    paint: ChartPaint,
) {
    gaps.forEach { gap ->
        val from = plot.x(gap.from)
        val to = plot.x(gap.to)
        drawHatch(from, to, plot.height, paint.colors.hatch)
        val title = paint.measure(paint.texts.noRecord, paint.styles.labelMutedStrong)
        val range = paint.measure(gap.slots(), paint.styles.label)
        val width = maxOf(title.size.width, range.size.width)
        if (to - from >= width + GapLabelPadding.toPx() * 2) {
            val center = (from + to) / 2
            val y = GapLabelTop.toPx()
            drawText(title, topLeft = Offset(center - title.size.width / 2f, y))
            drawText(range, topLeft = Offset(center - range.size.width / 2f, y + title.size.height))
        }
    }
}

private fun ChartGap.slots(): String =
    if (firstMissing == lastMissing) {
        firstMissing.formatTime()
    } else {
        "${firstMissing.formatTime()} – ${lastMissing.formatTime()}"
    }

/** Diagonal lines every [HatchStep] between [from] and [to]: the mark of "no record", not only a color. */
internal fun DrawScope.drawHatch(
    from: Float,
    to: Float,
    height: Float,
    color: Color,
) {
    val step = HatchStep.toPx()
    val stroke = HatchWidth.toPx()
    clipRect(left = from, top = 0f, right = to, bottom = height) {
        var x = from - height
        while (x < to) {
            drawLine(color, Offset(x, height), Offset(x + height, 0f), strokeWidth = stroke)
            x += step
        }
    }
}

private fun DrawScope.drawPriceAxis(
    plot: ChartGeometry,
    labels: List<TextLayoutResult>,
    gridColor: Color,
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(GridDash.toPx(), GridDash.toPx()))
    plot.priceMarks().zip(labels).forEach { (mark, label) ->
        if (mark.position < plot.height) {
            drawLine(
                gridColor,
                Offset(0f, mark.position),
                Offset(plot.width, mark.position),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash,
            )
        }
        drawText(label, topLeft = Offset(-AxisGap.toPx() - label.size.width, mark.position - label.size.height / 2f))
    }
}

/** Marks every 4 h (the date under midnight) and "Agora" with the time of the last point at the end. */
private fun DrawScope.drawTimeAxis(
    data: ChartData,
    plot: ChartGeometry,
    now: TextLayoutResult,
    paint: ChartPaint,
) {
    val baseline = plot.height
    val gap = AxisGap.toPx()
    val axis = paint.colors.axis
    val stroke = 1.dp.toPx()
    val nowLeft = plot.width - now.size.width / 2f
    drawLine(axis, Offset(0f, baseline), Offset(plot.width, baseline), stroke)
    plot.timeMarks().forEach { mark ->
        val label = paint.measure(mark.value.formatTime(), paint.styles.label)
        val labelLeft = mark.position - label.size.width / 2f
        if (labelLeft + label.size.width + gap <= nowLeft) {
            drawLine(axis, Offset(mark.position, baseline), Offset(mark.position, baseline + gap / 2), stroke)
            drawText(label, topLeft = Offset(labelLeft, baseline + gap))
            if (mark.value.toBrasilia().hour == 0) {
                val date = paint.measure(mark.value.formatDayMonth(), paint.styles.label)
                drawText(
                    date,
                    topLeft = Offset(mark.position - date.size.width / 2f, baseline + gap + label.size.height),
                )
            }
        }
    }
    drawLine(axis, Offset(plot.width, baseline), Offset(plot.width, baseline + gap / 2), stroke)
    drawText(now, topLeft = Offset(nowLeft, baseline + gap))
    val time = paint.measure(data.xRange.end.formatTime(), paint.styles.label)
    drawText(time, topLeft = Offset(plot.width - time.size.width / 2f, baseline + gap + now.size.height))
}

/** A soft area under each buy segment, down to the bottom of the plot. */
private fun DrawScope.drawArea(
    segments: List<List<Offset>>,
    bottom: Float,
    color: Color,
) {
    segments.filter { it.size > 1 }.forEach { segment ->
        val path = Path()
        path.moveTo(segment.first().x, bottom)
        segment.forEach { path.lineTo(it.x, it.y) }
        path.lineTo(segment.last().x, bottom)
        path.close()
        drawPath(path, color)
    }
}

/** The last point of each series: square for sell under circle for buy, so both show when they meet. */
private fun DrawScope.drawLastPoint(
    data: ChartData,
    plot: ChartGeometry,
    colors: ChartColors,
) {
    val buy = data.summary.buy.last
    val sell = data.summary.sell.last
    val side = LastMarker.toPx()
    drawMarker(Offset(plot.x(sell.at), plot.y(sell.price)), colors.sell, SeriesStyle.Dashed, side, colors.card)
    drawMarker(Offset(plot.x(buy.at), plot.y(buy.price)), colors.buy, SeriesStyle.Solid, side, colors.card)
}

private fun endTags(
    data: ChartData,
    paint: ChartPaint,
): List<EndTag> {
    val buy = data.summary.buy.last.price
    val sell = data.summary.sell.last.price

    fun tag(
        price: QuotePrice,
        color: Color,
    ) = EndTag(paint.measure(price.formatNumber(), paint.styles.labelStrong.copy(color = color)), color, price)
    return if (buy == sell) {
        listOf(tag(buy, paint.colors.buy))
    } else {
        listOf(tag(sell, paint.colors.sell), tag(buy, paint.colors.buy))
    }
}

/** The tags right of the plot, at the height of their price; pushed apart when they would overlap. */
private fun DrawScope.drawTags(
    tags: List<EndTag>,
    caption: TextLayoutResult?,
    plot: ChartGeometry,
    background: Color,
) {
    val padX = TagPaddingX.toPx()
    val padY = TagPaddingY.toPx()
    val height =
        tags
            .first()
            .layout.size.height + padY * 2
    val centers = tags.map { plot.y(it.price) }.toMutableList()
    if (centers.size == 2 && abs(centers[0] - centers[1]) < height + TagSpacing.toPx()) {
        val middle = (centers[0] + centers[1]) / 2
        val half = (height + TagSpacing.toPx()) / 2
        val upper = if (centers[0] <= centers[1]) 0 else 1
        centers[upper] = middle - half
        centers[1 - upper] = middle + half
    }
    val left = plot.width + AxisGap.toPx()
    tags.zip(centers).forEach { (tag, center) ->
        val top = center.coerceIn(height / 2, maxOf(height / 2, plot.height - height / 2)) - height / 2
        val size = Size(tag.layout.size.width + padX * 2, height)
        val corner = CornerRadius(TagCorner.toPx())
        drawRoundRect(background, Offset(left, top), size, corner)
        drawRoundRect(tag.color, Offset(left, top), size, corner, style = Stroke(TagBorder.toPx()))
        drawText(tag.layout, topLeft = Offset(left + padX, top + padY))
        caption?.let { drawText(it, topLeft = Offset(left, top + height + TagSpacing.toPx())) }
    }
}

private fun DrawScope.drawHover(
    hover: HoverPoint,
    plot: ChartGeometry,
    paint: ChartPaint,
) {
    val x = plot.x(hover.at)
    val dash = PathEffect.dashPathEffect(floatArrayOf(CrossDash.toPx(), CrossDash.toPx()))
    drawLine(paint.colors.muted, Offset(x, 0f), Offset(x, plot.height), strokeWidth = 1.dp.toPx(), pathEffect = dash)
    hover.point?.let { point ->
        val side = HoverMarker.toPx()
        val card = paint.colors.card
        drawMarker(Offset(x, plot.y(point.sellPrice)), paint.colors.sell, SeriesStyle.Dashed, side, card)
        drawMarker(Offset(x, plot.y(point.buyPrice)), paint.colors.buy, SeriesStyle.Solid, side, card)
    }
    drawTooltip(hover, x, plot, paint)
}

private fun tooltipRows(
    point: HistoryPoint,
    paint: ChartPaint,
): List<TooltipRow> {
    val styles = paint.styles
    val spread = PriceDifference.between(point.buyPrice, point.sellPrice).format()
    return listOf(
        TooltipRow(
            paint.measure(paint.texts.tooltip.buy, styles.body),
            paint.measure(point.buyPrice.format(), styles.bodyStrong),
            paint.colors.buy to SeriesStyle.Solid,
            dividerAbove = false,
        ),
        TooltipRow(
            paint.measure(paint.texts.tooltip.sell, styles.body),
            paint.measure(point.sellPrice.format(), styles.bodyStrong),
            paint.colors.sell to SeriesStyle.Dashed,
            dividerAbove = false,
        ),
        TooltipRow(
            paint.measure(paint.texts.tooltip.spread, styles.body),
            paint.measure(spread, styles.bodyStrong),
            marker = null,
            dividerAbove = true,
        ),
    )
}

/** Date and time of the slot, then buy, sell and spread, or "Sem registro neste horário". */
private fun DrawScope.drawTooltip(
    hover: HoverPoint,
    x: Float,
    plot: ChartGeometry,
    paint: ChartPaint,
) {
    val pad = TooltipPadding.toPx()
    val rowGap = TooltipRowGap.toPx()
    val swatch = TooltipSwatch.toPx() + TooltipSwatchGap.toPx()
    val header = paint.measure(hover.at.format(), paint.styles.bodyStrong)
    val rows = hover.point?.let { tooltipRows(it, paint) }.orEmpty()
    val message = if (hover.point == null) paint.measure(paint.texts.noRecordHere, paint.styles.body) else null
    val rowWidth =
        rows.maxOfOrNull { row ->
            swatch + row.label.size.width + TooltipValueGap.toPx() +
                row.value.size.width
        }
    val content = maxOf(header.size.width + 0f, rowWidth ?: 0f, (message?.size?.width ?: 0) + 0f)
    val rowsHeight = rows.sumOf { it.value.size.height } + rowGap * rows.size + rows.count { it.dividerAbove } * rowGap
    val messageHeight = message?.let { it.size.height + rowGap } ?: 0f
    val size = Size(content + pad * 2, pad * 2 + header.size.height + rowsHeight + messageHeight)
    val offset = TooltipOffset.toPx()
    val left = (if (x + offset + size.width <= plot.width) x + offset else x - offset - size.width).coerceAtLeast(0f)
    val topLeft = Offset(left, TooltipTop.toPx())
    val corner = CornerRadius(TooltipCorner.toPx())
    drawRoundRect(paint.colors.tooltip, topLeft, size, corner)
    drawRoundRect(paint.colors.tooltipBorder, topLeft, size, corner, style = Stroke(1.dp.toPx()))

    var y = topLeft.y + pad
    drawText(header, topLeft = Offset(left + pad, y))
    y += header.size.height
    message?.let { drawText(it, topLeft = Offset(left + pad, y + rowGap)) }
    rows.forEach { row ->
        y += rowGap
        if (row.dividerAbove) {
            drawLine(paint.colors.tooltipBorder, Offset(left + pad, y), Offset(left + size.width - pad, y), 1.dp.toPx())
            y += rowGap
        }
        val middle = y + row.value.size.height / 2f
        row.marker?.let { (color, style) ->
            drawMarker(Offset(left + pad + TooltipSwatch.toPx() / 2, middle), color, style, TooltipSwatch.toPx())
        }
        val labelLeft = left + pad + if (row.marker != null) swatch else 0f
        drawText(row.label, topLeft = Offset(labelLeft, middle - row.label.size.height / 2f))
        drawText(row.value, topLeft = Offset(left + size.width - pad - row.value.size.width, y))
        y += row.value.size.height
    }
}
