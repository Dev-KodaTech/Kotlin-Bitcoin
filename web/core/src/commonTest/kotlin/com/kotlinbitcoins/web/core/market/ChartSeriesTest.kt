package com.kotlinbitcoins.web.core.market

import com.kotlinbitcoins.web.core.model.HistoryPoint
import com.kotlinbitcoins.web.core.model.QuoteHistory
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.support.successValue
import com.kotlinbitcoins.web.core.time.ApiInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class ChartSeriesTest {
    private fun at(time: String): ApiInstant = ApiInstant.parseApi("2026-11-09T$time:00-03:00").successValue()

    private fun price(raw: String): QuotePrice = QuotePrice.parseApi(raw).successValue()

    private fun point(
        time: String,
        buy: String,
        sell: String = buy,
    ): HistoryPoint = HistoryPoint(at(time), price(buy), price(sell))

    @Test
    fun `consecutive slots of 10 minutes are one segment`() {
        val points = listOf(point("08:00", "1.00"), point("08:10", "2.00"), point("08:20", "3.00"))

        assertEquals(listOf(points), ChartSeries.segments(points))
    }

    @Test
    fun `a missing slot breaks the segment and no point is invented (FR-033)`() {
        val first = point("08:00", "1.00")
        val second = point("08:10", "2.00")
        val afterGap = point("08:30", "4.00")
        val last = point("08:40", "5.00")

        val segments = ChartSeries.segments(listOf(first, second, afterGap, last))

        assertEquals(listOf(listOf(first, second), listOf(afterGap, last)), segments)
        assertEquals(4, segments.sumOf { it.size })
    }

    @Test
    fun `an isolated point is a segment of one`() {
        val before = point("08:00", "1.00")
        val isolated = point("08:30", "2.00")
        val after = point("09:00", "3.00")

        val segments = ChartSeries.segments(listOf(before, isolated, after))

        assertEquals(listOf(listOf(before), listOf(isolated), listOf(after)), segments)
    }

    @Test
    fun `no points, no segments and no chart`() {
        assertEquals(emptyList(), ChartSeries.segments(emptyList()))
        assertNull(ChartSeries.chartData(QuoteHistory(emptyList())))
    }

    @Test
    fun `the series keep the segments with the price of each side`() {
        val history =
            QuoteHistory(
                listOf(
                    point("08:00", "100.00", "101.00"),
                    point("08:10", "110.00", "111.00"),
                    point("08:30", "90.5", "91.5"),
                ),
            )

        val data = assertNotNull(ChartSeries.chartData(history))

        assertEquals(
            listOf(
                listOf(ChartPoint(at("08:00"), price("100.00")), ChartPoint(at("08:10"), price("110.00"))),
                listOf(ChartPoint(at("08:30"), price("90.5"))),
            ),
            data.buy,
        )
        assertEquals(
            listOf(listOf(price("101.00"), price("111.00")), listOf(price("91.5"))),
            data.sell.map { s ->
                s.map { it.price }
            },
        )
    }

    @Test
    fun `the time range is the 24 hours that end at the last point`() {
        val data =
            assertNotNull(ChartSeries.chartData(QuoteHistory(listOf(point("08:00", "1.00"), point("08:10", "1.00")))))

        assertEquals(at("08:10"), data.xRange.end)
        assertEquals(ApiInstant.parseApi("2026-11-08T08:10:00-03:00").successValue(), data.xRange.start)
    }

    @Test
    fun `the price range covers both series`() {
        val history = QuoteHistory(listOf(point("08:00", "100.00", "105.00"), point("08:10", "99.99", "120.12345678")))

        val data = assertNotNull(ChartSeries.chartData(history))

        assertEquals(PriceRange(price("99.99"), price("120.12345678")), data.yRange)
    }

    @Test
    fun `the summary has the exact minimum, maximum and last of each series (FR-034)`() {
        val history =
            QuoteHistory(
                listOf(
                    point("08:00", "400000.12345678", "401000.00"),
                    point("08:10", "399999.5", "402000.00"),
                    point("08:30", "400500.00", "400900.01"),
                ),
            )

        val summary = assertNotNull(ChartSeries.chartData(history)).summary

        assertEquals(price("399999.5"), summary.buy.min)
        assertEquals(price("400500.00"), summary.buy.max)
        assertEquals(ChartPoint(at("08:30"), price("400500.00")), summary.buy.last)
        assertEquals(price("400900.01"), summary.sell.min)
        assertEquals(price("402000.00"), summary.sell.max)
        assertEquals(ChartPoint(at("08:30"), price("400900.01")), summary.sell.last)
        // The exact values reach the text: every received decimal.
        assertEquals("R\$\u00A0399.999,50", summary.buy.min.format())
    }

    @Test
    fun `table rows have one row per slot between the first and the last point, missing slots included`() {
        val first = point("08:00", "1.00")
        val second = point("08:10", "2.00")
        val afterGap = point("08:40", "3.00")

        val rows = TableRows.of(listOf(first, second, afterGap))

        assertEquals(
            listOf(
                TableRow.Recorded(first),
                TableRow.Recorded(second),
                TableRow.Missing(at("08:20")),
                TableRow.Missing(at("08:30")),
                TableRow.Recorded(afterGap),
            ),
            rows,
        )
    }

    @Test
    fun `table rows of an empty history are empty`() {
        assertEquals(emptyList(), TableRows.of(emptyList()))
    }

    @Test
    fun `the chart carries the table rows`() {
        val data =
            assertNotNull(ChartSeries.chartData(QuoteHistory(listOf(point("08:00", "1.00"), point("08:20", "1.00")))))

        assertIs<TableRow.Missing>(data.rows[1])
        assertEquals(3, data.rows.size)
    }

    @Test
    fun `time marks are every 4 hours on the hour in Brasilia inside the range`() {
        val data = assertNotNull(ChartSeries.chartData(QuoteHistory(listOf(point("08:10", "1.00")))))

        assertEquals(
            listOf("12:00", "16:00", "20:00", "00:00", "04:00", "08:00"),
            data.timeMarks.map { it.formatTime() },
        )
        assertTrue(data.timeMarks.all { it >= data.xRange.start && it <= data.xRange.end })
    }

    @Test
    fun `price marks are round multiples around both series and the scale spans them`() {
        val history = QuoteHistory(listOf(point("08:00", "400010.00", "400290.00"), point("08:10", "400000.00")))

        val data = assertNotNull(ChartSeries.chartData(history))

        assertEquals(
            listOf("400000.00", "400100.00", "400200.00", "400300.00"),
            data.priceMarks.map { it.toPlainString() },
        )
        assertEquals(PriceRange(price("400000.00"), price("400300.00")), data.scale)
        assertEquals(PriceRange(price("400000.00"), price("400290.00")), data.yRange)
    }

    @Test
    fun `a flat history is centered on a padded axis`() {
        val history = QuoteHistory(listOf(point("08:00", "400000.00"), point("08:10", "400000.00")))

        val data = assertNotNull(ChartSeries.chartData(history))

        assertEquals(
            listOf("399000.00", "399500.00", "400000.00", "400500.00", "401000.00"),
            data.priceMarks.map {
                it.toPlainString()
            },
        )
        assertEquals(PriceRange(price("399000.00"), price("401000.00")), data.scale)
    }

    @Test
    fun `tiny prices keep 4 marks from the minimum to the maximum`() {
        val history = QuoteHistory(listOf(point("08:00", "0.50", "5.00")))

        val marks = assertNotNull(ChartSeries.chartData(history)).priceMarks

        assertEquals(listOf("0.50", "2.00", "3.50", "5.00"), marks.map { it.toPlainString() })
    }

    @Test
    fun `the summary has the change over the window and the spread of the last point`() {
        val history =
            QuoteHistory(
                listOf(
                    point("08:00", "398000.00", "399000.00"),
                    point("08:10", "400100.00", "401100.50"),
                ),
            )

        val summary = assertNotNull(ChartSeries.chartData(history)).summary

        assertEquals("+0,52%", summary.buy.change.format())
        assertEquals("+0,52%", summary.sell.change.format())
        assertEquals("R\$\u00A01.000,50", summary.spread.format())
    }

    @Test
    fun `gaps cover the start of the window and the missing slots between segments`() {
        val history =
            QuoteHistory(listOf(point("07:30", "1.00"), point("07:40", "1.00"), point("08:10", "1.00")))

        val data = assertNotNull(ChartSeries.chartData(history))

        assertEquals(
            listOf(
                ChartGap(data.xRange.start, at("07:30"), data.xRange.start, at("07:20")),
                ChartGap(at("07:40"), at("08:10"), at("07:50"), at("08:00")),
            ),
            data.gaps,
        )
    }

    @Test
    fun `a full window has no gap and a coverage of 100 percent`() {
        val start = at("08:10").instant - 24.hours
        val points =
            (0..144).map { slot ->
                HistoryPoint(ApiInstant.of(start + ChartSeries.SLOT * slot), price("1.00"), price("1.00"))
            }

        val data = assertNotNull(ChartSeries.chartData(QuoteHistory(points)))

        assertEquals(emptyList(), data.gaps)
        assertEquals(ChartCoverage(145, 145), data.coverage)
        assertEquals(100, data.coverage.percent)
    }

    @Test
    fun `coverage counts the recorded slots of the window`() {
        val data =
            assertNotNull(ChartSeries.chartData(QuoteHistory(listOf(point("08:00", "1.00"), point("08:10", "1.00")))))

        assertEquals(ChartCoverage(2, 145), data.coverage)
        assertEquals(1, data.coverage.percent)
    }
}
