package com.kotlinbitcoins.web.core.market

import com.kotlinbitcoins.web.core.model.HistoryPoint
import com.kotlinbitcoins.web.core.model.QuoteHistory
import com.kotlinbitcoins.web.core.money.PriceAxis
import com.kotlinbitcoins.web.core.money.PriceDifference
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.money.VariationPercent
import com.kotlinbitcoins.web.core.time.ApiInstant
import com.kotlinbitcoins.web.core.time.BrasiliaTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** One price of one series at one slot. */
data class ChartPoint(
    val at: ApiInstant,
    val price: QuotePrice,
)

/** Points drawn as one line; consecutive segments are never joined (FR-033). */
typealias Segment = List<ChartPoint>

data class TimeRange(
    val start: ApiInstant,
    val end: ApiInstant,
)

data class PriceRange(
    val min: QuotePrice,
    val max: QuotePrice,
)

/** Exact minimum, maximum and last value of one series (FR-034), and its [change] from the first to the last point. */
data class SeriesSummary(
    val min: QuotePrice,
    val max: QuotePrice,
    val last: ChartPoint,
    val change: VariationPercent,
)

/** Both series and the [spread] (sell − buy) of the last point. */
data class ChartSummary(
    val buy: SeriesSummary,
    val sell: SeriesSummary,
    val spread: PriceDifference,
)

/**
 * A stretch of the 24 h window without record, drawn hatched from [from] to [to] (the points around it, or the start
 * of the window); [firstMissing] and [lastMissing] are its first and last empty slots.
 */
data class ChartGap(
    val from: ApiInstant,
    val to: ApiInstant,
    val firstMissing: ApiInstant,
    val lastMissing: ApiInstant,
)

/** Recorded 10 min slots out of the [totalSlots] of the 24 h window. */
data class ChartCoverage(
    val recordedSlots: Int,
    val totalSlots: Int,
) {
    /** Whole percent, truncated: 86 for 125 of 145 slots. */
    val percent: Int get() = recordedSlots * PERCENT / totalSlots

    private companion object {
        const val PERCENT = 100
    }
}

/** A line of the data table: a recorded slot, or a slot without record shown as "sem registro". */
sealed interface TableRow {
    data class Recorded(
        val point: HistoryPoint,
    ) : TableRow

    data class Missing(
        val slotAt: ApiInstant,
    ) : TableRow
}

/**
 * Everything the price chart shows, prepared without floating point (data-model §6, W-13): the two series in
 * segments, the 24 h time range ending at the last point, the price range of both series ([yRange]) and the range of
 * the axis around it ([scale], from the first to the last of [priceMarks]: round multiples of one step), the time marks
 * (every 4 h on the hour in Brasília), the textual summary, the table rows, the [gaps] and the [coverage].
 */
data class ChartData(
    val buy: List<Segment>,
    val sell: List<Segment>,
    val xRange: TimeRange,
    val yRange: PriceRange,
    val scale: PriceRange,
    val summary: ChartSummary,
    val rows: List<TableRow>,
    val timeMarks: List<ApiInstant>,
    val priceMarks: List<QuotePrice>,
    val gaps: List<ChartGap>,
    val coverage: ChartCoverage,
)

/** Pure preparation of the quote history for the chart; nothing is interpolated or invented. */
object ChartSeries {
    /** Distance between two consecutive slots of the history. */
    val SLOT = 10.minutes

    private val WINDOW = 24.hours
    private val TIME_MARK_STEP = 4.hours
    private const val TIME_MARK_HOURS = 4
    private const val PRICE_MARK_STEPS = 3

    /** The 10 min slots of the 24 h window, both ends included. */
    private val TOTAL_SLOTS = (WINDOW.inWholeMinutes / SLOT.inWholeMinutes).toInt() + 1

    /** Splits the points (time order) whenever the next slot is more than 10 min after the previous one. */
    fun segments(points: List<HistoryPoint>): List<List<HistoryPoint>> {
        val segments = mutableListOf<MutableList<HistoryPoint>>()
        points.forEachIndexed { index, point ->
            val previous = points.getOrNull(index - 1)
            if (previous == null || point.slotAt.instant - previous.slotAt.instant > SLOT) {
                segments += mutableListOf(point)
            } else {
                segments.last() += point
            }
        }
        return segments
    }

    /** The chart of [history], or null when there is no point (the empty state, US8-5). */
    fun chartData(history: QuoteHistory): ChartData? {
        val points = history.points
        if (points.isEmpty()) return null
        val segments = segments(points)
        val buy = segments.map { segment -> segment.map { ChartPoint(it.slotAt, it.buyPrice) } }
        val sell = segments.map { segment -> segment.map { ChartPoint(it.slotAt, it.sellPrice) } }
        val end = points.last().slotAt
        val xRange = TimeRange(ApiInstant.of(end.instant - WINDOW), end)
        val all = points.flatMap { listOf(it.buyPrice, it.sellPrice) }
        val yRange = PriceRange(all.min(), all.max())
        val priceMarks = priceMarks(yRange)
        val last = points.last()
        return ChartData(
            buy = buy,
            sell = sell,
            xRange = xRange,
            yRange = yRange,
            scale = PriceRange(priceMarks.first(), priceMarks.last()),
            summary =
                ChartSummary(summary(buy), summary(sell), PriceDifference.between(last.buyPrice, last.sellPrice)),
            rows = TableRows.of(points),
            timeMarks = timeMarks(xRange),
            priceMarks = priceMarks,
            gaps = gaps(segments, xRange),
            coverage = ChartCoverage(points.count { it.slotAt >= xRange.start }, TOTAL_SLOTS),
        )
    }

    private fun summary(series: List<Segment>): SeriesSummary {
        val points = series.flatten()
        return SeriesSummary(
            min = points.minOf { it.price },
            max = points.maxOf { it.price },
            last = points.last(),
            change = VariationPercent.between(points.first().price, points.last().price),
        )
    }

    /** Before the first point (when it starts after the window) and between consecutive segments. */
    private fun gaps(
        segments: List<List<HistoryPoint>>,
        range: TimeRange,
    ): List<ChartGap> {
        val first = segments.first().first().slotAt
        val leading =
            if (first > range.start) {
                listOf(ChartGap(range.start, first, range.start, ApiInstant.of(first.instant - SLOT)))
            } else {
                emptyList()
            }
        val between =
            segments.zipWithNext { before, after ->
                val from = before.last().slotAt
                val to = after.first().slotAt
                ChartGap(from, to, ApiInstant.of(from.instant + SLOT), ApiInstant.of(to.instant - SLOT))
            }
        return leading + between
    }

    /** Every 4 h on the hour in Brasília (00:00, 04:00, …) inside [range]. */
    private fun timeMarks(range: TimeRange): List<ApiInstant> {
        val startDay = range.start.toBrasilia().date
        val midnight = LocalDateTime(startDay.year, startDay.month, startDay.day, 0, 0).toInstant(BrasiliaTime.zone)
        return generateSequence(midnight) { it + TIME_MARK_STEP }
            .takeWhile { it <= range.end.instant }
            .filter { it >= range.start.instant && it.toLocalHour() % TIME_MARK_HOURS == 0 }
            .map(ApiInstant::of)
            .toList()
    }

    private fun kotlin.time.Instant.toLocalHour(): Int = ApiInstant.of(this).toBrasilia().hour

    /**
     * Round marks around the range ([PriceAxis]): up to 6 multiples of one step, and 5 around a flat price. When the
     * bottom of such an axis would not be a price (tiny values), 4 prices from the minimum to the maximum (one when
     * they are equal), the inner ones truncated to 2 decimals.
     */
    private fun priceMarks(range: PriceRange): List<QuotePrice> =
        PriceAxis.marks(range.min, range.max)
            ?: if (range.min == range.max) {
                listOf(range.min)
            } else {
                (0..PRICE_MARK_STEPS).mapNotNull { QuotePrice.axisMark(range.min, range.max, it, PRICE_MARK_STEPS) }
            }
}

/** One row per 10 min slot from the first to the last point; a slot without point is [TableRow.Missing]. */
object TableRows {
    fun of(points: List<HistoryPoint>): List<TableRow> {
        val rows = mutableListOf<TableRow>()
        points.forEachIndexed { index, point ->
            val previous = points.getOrNull(index - 1)
            if (previous != null) {
                var slot = previous.slotAt.instant + ChartSeries.SLOT
                while (slot < point.slotAt.instant) {
                    rows += TableRow.Missing(ApiInstant.of(slot))
                    slot += ChartSeries.SLOT
                }
            }
            rows += TableRow.Recorded(point)
        }
        return rows
    }
}
