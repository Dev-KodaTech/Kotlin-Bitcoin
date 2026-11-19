package com.kotlinbitcoins.web.ui.chart

import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.market.ChartData
import com.kotlinbitcoins.web.core.market.ChartSeries
import com.kotlinbitcoins.web.core.model.HistoryPoint
import com.kotlinbitcoins.web.core.model.QuoteHistory
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.time.ApiInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ChartGeometryTest {
    private val width = 240f
    private val height = 100f

    private fun <T> Outcome<*, T>.value(): T = (this as? Outcome.Success<T>)?.value ?: fail("expected a value")

    private fun at(raw: String): ApiInstant = ApiInstant.parseApi(raw).value()

    private fun price(raw: String): QuotePrice = QuotePrice.parseApi(raw).value()

    private fun point(
        slotAt: String,
        buy: String,
        sell: String,
    ): HistoryPoint = HistoryPoint(at(slotAt), price(buy), price(sell))

    /** Last point at 08:10 of 09/11: the 24 h window starts at 08:10 of 08/11. */
    private fun chart(vararg points: HistoryPoint): ChartData =
        assertNotNull(ChartSeries.chartData(QuoteHistory(points.toList())))

    private val data =
        chart(
            point("2026-11-09T07:50:00-03:00", "400000.00", "401000.00"),
            point("2026-11-09T08:00:00-03:00", "400500.00", "401500.00"),
            point("2026-11-09T08:10:00-03:00", "400250.00", "402000.00"),
        )

    @Test
    fun `instants map to x inside the 24 hours`() {
        val geometry = ChartGeometry(data, width, height)

        assertEquals(0f, geometry.x(at("2026-11-08T08:10:00-03:00")), TOLERANCE)
        assertEquals(width / 2, geometry.x(at("2026-11-08T20:10:00-03:00")), TOLERANCE)
        assertEquals(width, geometry.x(at("2026-11-09T08:10:00-03:00")), TOLERANCE)
        // Outside the window: clamped to the plot.
        assertEquals(0f, geometry.x(at("2026-11-07T08:10:00-03:00")), TOLERANCE)
    }

    @Test
    fun `prices map to y between the minimum (bottom) and the maximum (top)`() {
        val geometry = ChartGeometry(data, width, height)

        assertEquals(height, geometry.y(price("400000.00")), TOLERANCE)
        assertEquals(0f, geometry.y(price("402000.00")), TOLERANCE)
        assertEquals(height / 2, geometry.y(price("401000.00")), TOLERANCE)
    }

    @Test
    fun `a flat chart is drawn in the middle`() {
        val flat = chart(point("2026-11-09T08:10:00-03:00", "400000.00", "400000.00"))

        assertEquals(height / 2, ChartGeometry(flat, width, height).y(price("400000.00")), TOLERANCE)
    }

    @Test
    fun `a position becomes the nearest 10 minute slot of the window`() {
        val geometry = ChartGeometry(data, width, height)

        assertEquals(at("2026-11-09T08:10:00-03:00"), geometry.slotAt(width))
        assertEquals(at("2026-11-08T08:10:00-03:00"), geometry.slotAt(0f))
        assertEquals(at("2026-11-08T20:10:00-03:00"), geometry.slotAt(width / 2))
        // 1/144 of the width is one slot; a little past it still rounds to that slot.
        assertEquals(at("2026-11-09T08:00:00-03:00"), geometry.slotAt(width - width / 144 * 1.2f))
        // Outside the plot: the first or the last slot.
        assertEquals(at("2026-11-09T08:10:00-03:00"), geometry.slotAt(width + 50f))
        assertEquals(at("2026-11-08T08:10:00-03:00"), geometry.slotAt(-50f))
    }

    @Test
    fun `each segment becomes its own path and the gap is not joined`() {
        val gapped =
            chart(
                point("2026-11-09T07:30:00-03:00", "1.00", "2.00"),
                point("2026-11-09T07:40:00-03:00", "1.50", "2.50"),
                point("2026-11-09T08:00:00-03:00", "1.20", "2.20"),
            )
        val geometry = ChartGeometry(gapped, width, height)

        val buy = geometry.paths(gapped.buy)
        val sell = geometry.paths(gapped.sell)

        assertEquals(listOf(2, 1), buy.map { it.size })
        assertEquals(listOf(2, 1), sell.map { it.size })
        // The path after the gap starts at the x of 08:00, not at the end of the previous one.
        assertEquals(geometry.x(at("2026-11-09T08:00:00-03:00")), buy[1].single().x, TOLERANCE)
        assertTrue(buy[0].last().x < buy[1].first().x)
    }

    @Test
    fun `axis marks are placed at their instant and price`() {
        val geometry = ChartGeometry(data, width, height)

        val timeMarks = geometry.timeMarks()
        assertEquals(data.timeMarks, timeMarks.map { it.value })
        assertEquals(timeMarks.map { geometry.x(it.value) }, timeMarks.map { it.position })

        // Round marks of R$ 500 from R$ 400.000 to R$ 402.000.
        val priceMarks = geometry.priceMarks()
        assertEquals(5, priceMarks.size)
        assertEquals(height, priceMarks.first().position, TOLERANCE)
        assertEquals(0f, priceMarks.last().position, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
