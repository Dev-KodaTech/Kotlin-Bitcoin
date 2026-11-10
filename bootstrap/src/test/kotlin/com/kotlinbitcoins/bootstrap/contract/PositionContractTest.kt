package com.kotlinbitcoins.bootstrap.contract

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import com.kotlinbitcoins.bootstrap.support.JwtTestTokens
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
import java.util.UUID

/** US6 acceptance scenarios 1–3 over the assembled application (contracts/openapi.yaml: /me/investments). */
class PositionContractTest : ApplicationTestBase() {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var accountId: UUID

    @BeforeEach
    fun registerAccountAndStubQuote() {
        stubSource(buy = "399000.00", sell = "400000.00")
        val body =
            mockMvc
                .post("/api/v1/accounts") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"Ana Souza","email":"ana@example.com","password":"s3nha-forte"}"""
                }.andReturn()
                .response.contentAsString
        accountId = UUID.fromString(objectMapper.readTree(body)["id"].asText())
    }

    @Test
    fun `scenario 1 - bought at 400000 and now at 440000 the variation is plus 10 percent and the value 440`() {
        deposit("1000.00")
        purchase("400.00")
        stubSource(buy = "440000.00", sell = "441000.00")
        clock.advance(Duration.ofSeconds(11))

        position().andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.quote.buyPrice") { value("440000.00") }
            jsonPath("$.quote.sellPrice") { value("441000.00") }
            jsonPath("$.quote.quotedAt") { value("2026-11-09T08:10:11-03:00") }
            jsonPath("$.totalCurrentValue") { value("440.00") }
            jsonPath("$.investments.length()") { value(1) }
            jsonPath("$.investments[0].id") { isNotEmpty() }
            jsonPath("$.investments[0].purchasedAt") { value("2026-11-09T08:10:00-03:00") }
            jsonPath("$.investments[0].investedAmount") { value("400.00") }
            jsonPath("$.investments[0].purchaseQuote") { value("400000.00") }
            jsonPath("$.investments[0].btcAmount") { value("0.00100000") }
            jsonPath("$.investments[0].variationPercent") { value("10.00") }
            jsonPath("$.investments[0].currentValue") { value("440.00") }
        }
    }

    @Test
    fun `a fall in the buy price is a negative variation`() {
        deposit("1000.00")
        purchase("400.00")
        stubSource(buy = "360000.00", sell = "361000.00")
        clock.advance(Duration.ofSeconds(11))

        position().andExpect {
            status { isOk() }
            jsonPath("$.investments[0].variationPercent") { value("-10.00") }
            jsonPath("$.investments[0].currentValue") { value("360.00") }
            jsonPath("$.totalCurrentValue") { value("360.00") }
        }
    }

    @Test
    fun `investments come in FIFO order and the total sums them`() {
        deposit("1000.00")
        purchase("400.00")
        clock.advance(Duration.ofMinutes(5))
        purchase("200.00")

        position().andExpect {
            status { isOk() }
            jsonPath("$.investments.length()") { value(2) }
            jsonPath("$.investments[0].investedAmount") { value("400.00") }
            jsonPath("$.investments[1].investedAmount") { value("200.00") }
            jsonPath("$.totalCurrentValue") { value("598.50") }
        }
    }

    @Test
    fun `scenario 2 - an account without investments gets an empty list`() {
        position().andExpect {
            status { isOk() }
            jsonPath("$.investments.length()") { value(0) }
            jsonPath("$.totalCurrentValue") { value("0.00") }
            jsonPath("$.quote.buyPrice") { value("399000.00") }
        }
    }

    @Test
    fun `scenario 3 - closed investments do not appear`() {
        deposit("1000.00")
        purchase("400.00")
        insertClosedInvestment()

        position().andExpect {
            status { isOk() }
            jsonPath("$.investments.length()") { value(1) }
            jsonPath("$.investments[0].investedAmount") { value("400.00") }
            jsonPath("$.totalCurrentValue") { value("399.00") }
        }
    }

    @Test
    fun `with the source down and no quote of 60 seconds or less it is 503`() {
        quoteSource.stubFor(get(urlEqualTo(SOURCE_PATH)).willReturn(aResponse().withStatus(521)))

        position().andExpect {
            status { isServiceUnavailable() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("QUOTE_UNAVAILABLE") }
        }
    }

    @Test
    fun `without a valid token it is 401`() {
        listOf(null, "Bearer ${JwtTestTokens.MALFORMED}", "Bearer ${JwtTestTokens.expired(accountId, clock.now())}")
            .forEach { authorization ->
                mockMvc
                    .get("/api/v1/me/investments") {
                        authorization?.let { header(HttpHeaders.AUTHORIZATION, it) }
                    }.andExpect {
                        status { isUnauthorized() }
                        jsonPath("$.code") { value("UNAUTHORIZED") }
                    }
            }
    }

    private fun insertClosedInvestment() {
        jdbcTemplate.update(
            """
            INSERT INTO investments (id, account_id, purchased_at, fifo_sequence, invested_amount, purchase_quote,
                                     btc_amount_sats, status, origin, closed_at)
            VALUES (?, ?, ?, nextval('investments_fifo_seq'), 100.00, 400000.00, 25000, 'CLOSED', 'PURCHASE', ?)
            """.trimIndent(),
            UUID.randomUUID(),
            accountId,
            Timestamp.from(clock.now().minusSeconds(3600)),
            Timestamp.from(clock.now().minusSeconds(60)),
        )
    }

    private fun deposit(amount: String) {
        mockMvc
            .post("/api/v1/me/deposits") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.valid(accountId, clock.now())}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"amount":"$amount"}"""
            }.andExpect { status { isCreated() } }
    }

    private fun purchase(amount: String) {
        mockMvc
            .post("/api/v1/me/purchases") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.valid(accountId, clock.now())}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"amount":"$amount"}"""
            }.andExpect { status { isCreated() } }
    }

    private fun position(): ResultActionsDsl =
        mockMvc.get("/api/v1/me/investments") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.valid(accountId, clock.now())}")
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
