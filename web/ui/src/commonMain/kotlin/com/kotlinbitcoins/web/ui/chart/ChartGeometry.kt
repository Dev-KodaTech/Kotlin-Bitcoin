package com.kotlinbitcoins.web.ui.chart

import androidx.compose.ui.geometry.Offset
import com.kotlinbitcoins.web.core.market.ChartData
import com.kotlinbitcoins.web.core.market.ChartSeries
import com.kotlinbitcoins.web.core.market.Segment
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.time.ApiInstant
import kotlin.math.roundToInt

/** A mark of an axis: the exact [value] shown as text and its [position] in pixels along the axis. */
data class AxisMark<out T>(
    val value: T,
    val position: Float,
)

/**
 * Pixel geometry of the chart in a plot of [width] × [height] (origin at the top left). The **only** place where a
 * price or an instant becomes floating point (W-06, W-13; checkFloatConversionConfined): the conversion serves only to
 * place pixels, and every text uses the exact values of [ChartData].
 */
class ChartGeometry(
    private val data: ChartData,
    val width: Float,
    val height: Float,
) {
    private val startMillis = millis(data.xRange.start)
    private val spanMillis = (millis(data.xRange.end) - startMillis).coerceAtLeast(1)
    private val minPrice = number(data.scale.min)
    private val priceSpan = number(data.scale.max) - minPrice
    private val slots = (spanMillis / ChartSeries.SLOT.inWholeMilliseconds).toInt()

    /** 0 at the start of the 24 h window, [width] at its end; instants outside are clamped. */
    fun x(at: ApiInstant): Float {
        val fraction = (millis(at) - startMillis).toDouble() / spanMillis.toDouble()
        return (fraction.coerceIn(0.0, 1.0) * width).toFloat()
    }

    /**
     * The 10 min slot nearest to [x] (pixels from the left of the plot), counted back from the end of the window:
     * what the pointer is over. Positions outside the plot give the first or the last slot.
     */
    fun slotAt(x: Float): ApiInstant {
        if (width <= 0f) return data.xRange.end
        val fromEnd = ((1.0 - (x / width).toDouble().coerceIn(0.0, 1.0)) * slots).roundToInt()
        return ApiInstant.of(data.xRange.end.instant - ChartSeries.SLOT * fromEnd)
    }

    /** [height] at the bottom of the axis ([ChartData.scale]), 0 at its top; a single-price axis is the middle. */
    fun y(price: QuotePrice): Float {
        if (priceSpan <= 0.0) return height / 2
        val fraction = (number(price) - minPrice) / priceSpan
        return ((1.0 - fraction.coerceIn(0.0, 1.0)) * height).toFloat()
    }

    /** One list of points per segment: each one is drawn as its own path, so a gap is never joined (FR-033). */
    fun paths(series: List<Segment>): List<List<Offset>> =
        series.map { segment -> segment.map { Offset(x(it.at), y(it.price)) } }

    fun timeMarks(): List<AxisMark<ApiInstant>> = data.timeMarks.map { AxisMark(it, x(it)) }

    fun priceMarks(): List<AxisMark<QuotePrice>> = data.priceMarks.map { AxisMark(it, y(it)) }

    private fun millis(at: ApiInstant): Long = at.instant.toEpochMilliseconds()

    /** Pixel use only: the exact value stays in [QuotePrice] for every text. */
    private fun number(price: QuotePrice): Double = price.toPlainString().toDouble()
}
