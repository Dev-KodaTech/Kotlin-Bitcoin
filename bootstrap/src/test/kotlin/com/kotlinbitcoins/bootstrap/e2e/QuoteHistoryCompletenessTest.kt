package com.kotlinbitcoins.bootstrap.e2e

import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import com.kotlinbitcoins.infrastructure.scheduling.CaptureQuoteSnapshotJob
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant

/**
 * SC-008: over 24 hours with the source available, the 10-minute capture leaves at least 143 of the 144 points in
 * the history. The clock walks the day slot by slot and the job runs at each one, as the scheduler would.
 */
class QuoteHistoryCompletenessTest : ApplicationTestBase() {
    @Autowired
    private lateinit var captureJob: CaptureQuoteSnapshotJob

    @Test
    fun `24 hours of captures leave at least 143 of the 144 points`() {
        val start = Instant.parse("2026-11-09T00:00:00Z")
        clock.set(start)
        val ana = api.register("Ana", "ana@example.com")

        repeat(SLOTS) { slot ->
            clock.advance(Duration.ofMinutes(SLOT_MINUTES))
            stubQuote(buy = "399000.00", sell = "${400_000 + slot}.00")
            captureJob.run()
        }
        clock.set(start.plus(Duration.ofHours(HOURS)))

        val points = api.json("/api/v1/quotes/history", ana)["points"].toList()
        assertThat(points.size).describedAs("points in the last 24 h").isGreaterThanOrEqualTo(MIN_POINTS)
        assertThat(points.map { it["slotAt"].asText() }).isSorted()
        assertThat(points.map { it["slotAt"].asText() }).doesNotHaveDuplicates()
    }

    private companion object {
        const val SLOTS = 144
        const val SLOT_MINUTES = 10L
        const val HOURS = 24L
        const val MIN_POINTS = 143
    }
}
