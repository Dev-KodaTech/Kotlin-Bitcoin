package com.kotlinbitcoins.bootstrap.contract

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import com.kotlinbitcoins.bootstrap.support.JwtTestTokens
import com.kotlinbitcoins.infrastructure.scheduling.CaptureQuoteSnapshotJob
import com.kotlinbitcoins.infrastructure.scheduling.PurgeQuoteHistoryJob
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * US10 acceptance scenarios 1–3 and quickstart step 20 over the assembled application, with the capture and purge
 * jobs called directly, WireMock as the source and the [com.kotlinbitcoins.bootstrap.support.MutableClock] moving
 * through the slots (contracts/openapi.yaml: /quotes/history, SC-009).
 */
class QuoteHistoryContractTest : ApplicationTestBase() {
    @Autowired
    private lateinit var captureJob: CaptureQuoteSnapshotJob

    @Autowired
    private lateinit var purgeJob: PurgeQuoteHistoryJob

    private val token = "Bearer ${JwtTestTokens.valid(UUID.randomUUID(), DEFAULT_NOW)}"

    @Test
    fun `scenario 1 - captures in 3 slots with the source down in the middle give 2 points, with no interpolation`() {
        stubSource(buy = "399000.00", sell = "400000.00")
        clock.set(Instant.parse("2026-11-09T11:10:00Z"))
        captureJob.run()

        sourceIsDown()
        clock.set(Instant.parse("2026-11-09T11:20:00Z"))
        captureJob.run()

        stubSource(buy = "399500.50", sell = "400500.50")
        clock.set(Instant.parse("2026-11-09T11:30:00Z"))
        captureJob.run()

        history().andExpect {
            status { isOk() }
            jsonPath("$.points.length()") { value(2) }
            jsonPath("$.points[0].slotAt") { value("2026-11-09T08:10:00-03:00") }
            jsonPath("$.points[0].buyPrice") { value("399000.00") }
            jsonPath("$.points[0].sellPrice") { value("400000.00") }
            jsonPath("$.points[1].slotAt") { value("2026-11-09T08:30:00-03:00") }
            jsonPath("$.points[1].buyPrice") { value("399500.50") }
            jsonPath("$.points[1].sellPrice") { value("400500.50") }
        }
        assertThat(slotRows()).hasSize(2)
    }

    @Test
    fun `the slot is the 10 minute mark of the capture, whatever the second it ran at`() {
        stubSource(buy = "399000.00", sell = "400000.00")
        clock.set(Instant.parse("2026-11-09T11:17:42Z"))

        captureJob.run()

        history().andExpect {
            status { isOk() }
            jsonPath("$.points.length()") { value(1) }
            jsonPath("$.points[0].slotAt") { value("2026-11-09T08:10:00-03:00") }
        }
        val capturedAt =
            jdbcTemplate.queryForObject("SELECT captured_at FROM quote_history", Timestamp::class.java)!!.toInstant()
        assertThat(capturedAt).isEqualTo(Instant.parse("2026-11-09T11:17:42Z"))
    }

    @Test
    fun `two captures in the same slot keep the first point`() {
        stubSource(buy = "399000.00", sell = "400000.00")
        clock.set(Instant.parse("2026-11-09T11:10:05Z"))
        captureJob.run()
        stubSource(buy = "1.00", sell = "2.00")
        clock.set(Instant.parse("2026-11-09T11:15:00Z"))
        captureJob.run()

        history().andExpect {
            jsonPath("$.points.length()") { value(1) }
            jsonPath("$.points[0].buyPrice") { value("399000.00") }
        }
    }

    @Test
    fun `scenario 2 - points come in ascending order and only the last 24 hours are returned`() {
        insertSnapshot(Instant.parse("2026-11-08T10:10:00Z"), "100.00") // 25 h ago
        insertSnapshot(Instant.parse("2026-11-08T11:10:00Z"), "200.00") // exactly 24 h ago: outside the window
        insertSnapshot(Instant.parse("2026-11-09T10:50:00Z"), "400.00")
        insertSnapshot(Instant.parse("2026-11-08T11:20:00Z"), "300.00") // inside, inserted out of order
        insertSnapshot(Instant.parse("2026-11-09T11:10:00Z"), "500.00")

        history().andExpect {
            status { isOk() }
            jsonPath("$.points.length()") { value(3) }
            jsonPath("$.points[0].buyPrice") { value("300.00") }
            jsonPath("$.points[1].buyPrice") { value("400.00") }
            jsonPath("$.points[2].buyPrice") { value("500.00") }
        }
    }

    @Test
    fun `the minutes of every point are multiples of 10`() {
        stubSource(buy = "399000.00", sell = "400000.00")
        listOf("2026-11-09T09:03:11Z", "2026-11-09T09:14:59Z", "2026-11-09T09:27:00Z", "2026-11-09T09:59:59Z")
            .forEach {
                clock.set(Instant.parse(it))
                captureJob.run()
            }
        clock.set(Instant.parse("2026-11-09T11:10:00Z"))

        history().andExpect {
            status { isOk() }
            jsonPath("$.points.length()") { value(4) }
            jsonPath("$.points[0].slotAt") { value("2026-11-09T06:00:00-03:00") }
            jsonPath("$.points[1].slotAt") { value("2026-11-09T06:10:00-03:00") }
            jsonPath("$.points[2].slotAt") { value("2026-11-09T06:20:00-03:00") }
            jsonPath("$.points[3].slotAt") { value("2026-11-09T06:50:00-03:00") }
        }
    }

    @Test
    fun `scenario 3 and SC-009 - the purge removes the record of 91 days and keeps the one of 89 days`() {
        val now = Instant.parse("2026-11-09T11:10:00Z")
        insertSnapshot(now.minus(Duration.ofDays(91)), "91.00")
        insertSnapshot(now.minus(Duration.ofDays(89)), "89.00")
        insertSnapshot(now, "1.00")

        purgeJob.run()

        assertThat(slotRows()).containsExactly(now.minus(Duration.ofDays(89)), now)
    }

    @Test
    fun `without points the history is an empty list`() {
        history().andExpect {
            status { isOk() }
            jsonPath("$.points.length()") { value(0) }
        }
    }

    @Test
    fun `without a valid token it is 401`() {
        val expired = JwtTestTokens.expired(UUID.randomUUID(), clock.now())
        listOf(null, "Bearer ${JwtTestTokens.MALFORMED}", "Bearer $expired")
            .forEach { authorization ->
                mockMvc
                    .get("/api/v1/quotes/history") {
                        authorization?.let { header(HttpHeaders.AUTHORIZATION, it) }
                    }.andExpect {
                        status { isUnauthorized() }
                        jsonPath("$.code") { value("UNAUTHORIZED") }
                    }
            }
    }

    private fun history(): ResultActionsDsl =
        mockMvc.get("/api/v1/quotes/history") { header(HttpHeaders.AUTHORIZATION, token) }

    private fun slotRows(): List<Instant> =
        jdbcTemplate
            .queryForList("SELECT slot_at FROM quote_history ORDER BY slot_at", Timestamp::class.java)
            .map { it.toInstant() }

    private fun insertSnapshot(
        slotAt: Instant,
        buy: String,
    ) {
        jdbcTemplate.update(
            "INSERT INTO quote_history (slot_at, buy_price, sell_price, captured_at) " +
                "VALUES (?, ?::numeric, ?::numeric, ?)",
            Timestamp.from(slotAt),
            buy,
            buy,
            Timestamp.from(slotAt),
        )
    }

    private fun stubSource(
        buy: String,
        sell: String,
    ) {
        quoteSource.stubFor(
            get(urlEqualTo(SOURCE_PATH)).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"ticker":{"buy":"$buy","sell":"$sell","date":1762686600}}"""),
            ),
        )
    }

    private fun sourceIsDown() {
        quoteSource.stubFor(get(urlEqualTo(SOURCE_PATH)).willReturn(aResponse().withStatus(503)))
    }

    private companion object {
        const val SOURCE_PATH = "/api/BTC/ticker/"
    }
}
