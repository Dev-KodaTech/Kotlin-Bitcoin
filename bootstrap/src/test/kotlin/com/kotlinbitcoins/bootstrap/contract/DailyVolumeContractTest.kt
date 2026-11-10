package com.kotlinbitcoins.bootstrap.contract

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import com.kotlinbitcoins.bootstrap.support.JwtTestTokens
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * US9 acceptance scenarios 1–5 and quickstart step 17, over the assembled application
 * (contracts/openapi.yaml: /market/daily-volume). The clock starts at 2026-11-09T11:10:00Z (08:10 in São Paulo).
 */
class DailyVolumeContractTest : ApplicationTestBase() {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var xId: UUID
    private lateinit var yId: UUID

    @BeforeEach
    fun registerAccountsAndStubQuote() {
        stubSource(buy = "400000.00", sell = "400000.00")
        xId = register("x@example.com")
        yId = register("y@example.com")
    }

    @Test
    fun `scenario 1 and quickstart 17 - totals add the purchases and redemptions of every client`() {
        deposit(xId, "1000.00")
        deposit(yId, "1000.00")
        purchase(xId, "400.00") // 0.00100000 BTC at 400000.00
        purchase(yId, "800.00") // 0.00200000 BTC
        redeem(xId, "200.00") // 0.00050000 BTC at 400000.00

        volume(xId).andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.date") { value("2026-11-09") }
            jsonPath("$.boughtBtc") { value("0.00300000") }
            jsonPath("$.soldBtc") { value("0.00050000") }
        }
        // Any authenticated client sees the same market totals.
        volume(yId).andExpect {
            status { isOk() }
            jsonPath("$.boughtBtc") { value("0.00300000") }
            jsonPath("$.soldBtc") { value("0.00050000") }
        }
    }

    @Test
    fun `scenario 2 - without operations both totals are zero`() {
        volume(xId).andExpect {
            status { isOk() }
            jsonPath("$.date") { value("2026-11-09") }
            jsonPath("$.boughtBtc") { value("0.00000000") }
            jsonPath("$.soldBtc") { value("0.00000000") }
        }
    }

    @Test
    fun `scenario 3 - operations of the previous local day are not counted`() {
        // 23:59:59 of 08/11 in São Paulo, one second before the current local day starts.
        insertBtcLine(xId, "PURCHASE", 100_000, Instant.parse("2026-11-09T02:59:59Z"))
        insertBtcLine(xId, "REDEMPTION", 50_000, Instant.parse("2026-11-09T02:59:59Z"))
        insertBtcLine(yId, "PURCHASE", 200_000, Instant.parse("2026-11-09T10:00:00Z"))

        volume(xId).andExpect {
            status { isOk() }
            jsonPath("$.boughtBtc") { value("0.00200000") }
            jsonPath("$.soldBtc") { value("0.00000000") }
        }
    }

    @Test
    fun `scenario 4 - a purchase at 22h local counts in the local day although it is the next day in UTC`() {
        clock.set(Instant.parse("2026-11-10T01:00:00Z")) // 22:00 of 09/11 in São Paulo, 10/11 in UTC
        deposit(xId, "1000.00")
        purchase(xId, "400.00")

        volume(xId).andExpect {
            status { isOk() }
            jsonPath("$.date") { value("2026-11-09") }
            jsonPath("$.boughtBtc") { value("0.00100000") }
        }
    }

    @Test
    fun `the day rolls over at midnight local, not at midnight UTC`() {
        insertBtcLine(xId, "PURCHASE", 100_000, Instant.parse("2026-11-10T02:59:59Z")) // 23:59:59 of 09/11 local
        insertBtcLine(xId, "PURCHASE", 200_000, Instant.parse("2026-11-10T03:00:00Z")) // 00:00 of 10/11 local

        clock.set(Instant.parse("2026-11-10T02:59:59Z"))
        volume(xId).andExpect {
            jsonPath("$.date") { value("2026-11-09") }
            jsonPath("$.boughtBtc") { value("0.00100000") }
        }

        clock.set(Instant.parse("2026-11-10T03:00:00Z"))
        volume(xId).andExpect {
            jsonPath("$.date") { value("2026-11-10") }
            jsonPath("$.boughtBtc") { value("0.00200000") }
        }
    }

    @Test
    fun `scenario 5 - the response only has the date and the totals, nothing that identifies a client`() {
        deposit(xId, "1000.00")
        purchase(xId, "400.00")

        val body =
            volume(yId)
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val json = objectMapper.readTree(body)

        assertThat(json.fieldNames().asSequence().toList())
            .containsExactlyInAnyOrder("date", "boughtBtc", "soldBtc")
        assertThat(body)
            .doesNotContain(xId.toString(), "x@example.com")
    }

    @Test
    fun `a reinvestment does not count as a purchase and a redemption counts only what was sold`() {
        deposit(xId, "1000.00")
        purchase(xId, "400.00") // A: 0.00100000 BTC
        purchase(xId, "200.00") // B: 0.00050000 BTC
        stubSource(buy = "200000.00", sell = "201000.00")
        clock.advance(Duration.ofMinutes(1))
        redeem(xId, "250.00") // A whole (0.001), B partial (0.00025), reinvesting 0.00025 BTC

        volume(xId).andExpect {
            status { isOk() }
            jsonPath("$.boughtBtc") { value("0.00150000") }
            jsonPath("$.soldBtc") { value("0.00125000") }
        }
    }

    @Test
    fun `without a valid token it is 401`() {
        listOf(null, "Bearer ${JwtTestTokens.MALFORMED}", "Bearer ${JwtTestTokens.expired(xId, clock.now())}")
            .forEach { authorization ->
                mockMvc
                    .get("/api/v1/market/daily-volume") {
                        authorization?.let { header(HttpHeaders.AUTHORIZATION, it) }
                    }.andExpect {
                        status { isUnauthorized() }
                        jsonPath("$.code") { value("UNAUTHORIZED") }
                    }
            }
    }

    private fun register(email: String): UUID {
        val body =
            mockMvc
                .post("/api/v1/accounts") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"Cliente","email":"$email","password":"s3nha-forte"}"""
                }.andReturn()
                .response.contentAsString
        return UUID.fromString(objectMapper.readTree(body)["id"].asText())
    }

    private fun insertBtcLine(
        account: UUID,
        type: String,
        sats: Long,
        at: Instant,
    ) {
        jdbcTemplate.update(
            "INSERT INTO transactions (id, account_id, type, amount, btc_amount_sats, quote, occurred_at) " +
                "VALUES (?, ?, ?, 10.00, ?, 400000.00, ?)",
            UUID.randomUUID(),
            account,
            type,
            sats,
            Timestamp.from(at),
        )
    }

    private fun deposit(
        account: UUID,
        amount: String,
    ) = post(account, "/api/v1/me/deposits", amount)

    private fun purchase(
        account: UUID,
        amount: String,
    ) = post(account, "/api/v1/me/purchases", amount)

    private fun redeem(
        account: UUID,
        amount: String,
    ) = post(account, "/api/v1/me/redemptions", amount)

    private fun post(
        account: UUID,
        path: String,
        amount: String,
    ) {
        mockMvc
            .post(path) {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.valid(account, clock.now())}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"amount":"$amount"}"""
            }.andExpect { status { isCreated() } }
    }

    private fun volume(account: UUID): ResultActionsDsl =
        mockMvc.get("/api/v1/market/daily-volume") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.valid(account, clock.now())}")
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

    private companion object {
        const val SOURCE_PATH = "/api/BTC/ticker/"
    }
}
