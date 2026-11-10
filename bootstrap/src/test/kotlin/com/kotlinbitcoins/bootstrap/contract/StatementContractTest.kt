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
 * US8 acceptance scenarios 1–4 and quickstart steps 15–16, over the assembled application
 * (contracts/openapi.yaml: /me/statement). The clock is fixed at 2026-11-09T11:10:00Z (08:10 in São Paulo).
 */
class StatementContractTest : ApplicationTestBase() {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var accountId: UUID

    @BeforeEach
    fun registerAccountAndStubQuote() {
        stubSource(buy = "400000.00", sell = "400000.00")
        accountId = register("ana@example.com")
    }

    @Test
    fun `scenario 1 - the default period leaves out lines older than 90 days`() {
        insertDeposit(accountId, "100.00", clock.now().minus(Duration.ofDays(100)))
        insertDeposit(accountId, "200.00", clock.now().minus(Duration.ofDays(89)))
        deposit("300.00")

        statement().andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.from") { value("2026-08-11T00:00:00-03:00") }
            jsonPath("$.to") { value("2026-11-09T08:10:00-03:00") }
            jsonPath("$.entries.length()") { value(2) }
            jsonPath("$.entries[0].amount") { value("300.00") }
            jsonPath("$.entries[1].amount") { value("200.00") }
        }
    }

    @Test
    fun `scenario 2 - a custom range is inclusive on both ends`() {
        // Business zone is UTC-3: 2026-11-01 00:00 local = 03:00Z and 2026-11-05 23:59 local = 2026-11-06 02:59Z.
        insertDeposit(accountId, "1.00", Instant.parse("2026-11-01T02:59:59Z")) // 23:59:59 of 10-31 → out
        insertDeposit(accountId, "2.00", Instant.parse("2026-11-01T03:00:00Z")) // 00:00 of the first day → in
        insertDeposit(accountId, "3.00", Instant.parse("2026-11-06T02:59:00Z")) // 23:59 of the last day → in
        insertDeposit(accountId, "4.00", Instant.parse("2026-11-06T03:00:00Z")) // 00:00 of the next day → out

        statement("?from=2026-11-01&to=2026-11-05").andExpect {
            status { isOk() }
            jsonPath("$.from") { value("2026-11-01T00:00:00-03:00") }
            jsonPath("$.entries.length()") { value(2) }
            jsonPath("$.entries[0].amount") { value("3.00") }
            jsonPath("$.entries[1].amount") { value("2.00") }
        }
    }

    @Test
    fun `from equal to is the whole day`() {
        insertDeposit(accountId, "2.00", Instant.parse("2026-11-01T03:00:00Z"))
        insertDeposit(accountId, "3.00", Instant.parse("2026-11-02T02:59:59Z"))

        statement("?from=2026-11-01&to=2026-11-01").andExpect {
            status { isOk() }
            jsonPath("$.entries.length()") { value(2) }
        }
    }

    @Test
    fun `scenario 3 and quickstart 16 - from after to is 422 INVALID_DATE_RANGE`() {
        statement("?from=2026-11-10&to=2026-11-01").andExpect {
            status { isUnprocessableEntity() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("INVALID_DATE_RANGE") }
            jsonPath("$.detail") { value("Intervalo de datas inválido.") }
        }
    }

    @Test
    fun `a malformed date or a single date is 422 INVALID_DATE_RANGE too`() {
        listOf("?from=2026-13-01&to=2026-11-09", "?from=abc&to=2026-11-09", "?from=2026-11-01", "?to=2026-11-09")
            .forEach { query ->
                statement(query).andExpect {
                    status { isUnprocessableEntity() }
                    jsonPath("$.code") { value("INVALID_DATE_RANGE") }
                }
            }
    }

    @Test
    fun `scenario 4 and quickstart 15 - purchases and redemptions carry quote and bitcoin, newest first`() {
        deposit("1000.00")
        clock.advance(Duration.ofMinutes(1))
        purchase("400.00")
        clock.advance(Duration.ofMinutes(1))
        purchase("200.00")
        stubSource(buy = "200000.00", sell = "201000.00")
        clock.advance(Duration.ofMinutes(1))
        redeem("250.00")

        val body =
            statement()
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val entries = objectMapper.readTree(body)["entries"].toList()

        // DEPOSIT, 2 × PURCHASE, 2 × REDEMPTION and 1 × REINVESTMENT.
        assertThat(entries.map { it["type"].asText() })
            .containsExactlyInAnyOrder("DEPOSIT", "PURCHASE", "PURCHASE", "REDEMPTION", "REDEMPTION", "REINVESTMENT")
        // Newest first: the three lines of the redemption, then the purchases, then the deposit.
        val times = entries.map { Instant.parse(it["occurredAt"].asText().let(::toUtc)) }
        assertThat(times).isSortedAccordingTo(Comparator.reverseOrder())
        assertThat(entries.take(3).map { it["type"].asText() })
            .containsExactlyInAnyOrder("REDEMPTION", "REDEMPTION", "REINVESTMENT")
        assertThat(entries.take(3).map { it["requestId"].asText() }.toSet()).hasSize(1)
        assertThat(entries.drop(3).map { it["requestId"].isNull }).containsOnly(true)

        val deposit = entries.single { it["type"].asText() == "DEPOSIT" }
        assertThat(deposit["amount"].asText()).isEqualTo("1000.00")
        assertThat(deposit["btcAmount"].isNull).isTrue()
        assertThat(deposit["quote"].isNull).isTrue()
        assertThat(deposit["investmentId"].isNull).isTrue()

        val firstPurchase = entries.single { it["type"].asText() == "PURCHASE" && it["amount"].asText() == "400.00" }
        assertThat(firstPurchase["btcAmount"].asText()).isEqualTo("0.00100000")
        assertThat(firstPurchase["quote"].asText()).isEqualTo("400000.00")
        assertThat(firstPurchase["investmentId"].isNull).isFalse()

        val redemptions = entries.filter { it["type"].asText() == "REDEMPTION" }
        assertThat(redemptions.map { it["amount"].asText() }).containsExactlyInAnyOrder("200.00", "50.00")
        assertThat(redemptions.map { it["quote"].asText() }.toSet()).containsExactly("200000.00")
        val reinvestment = entries.single { it["type"].asText() == "REINVESTMENT" }
        assertThat(reinvestment["btcAmount"].asText()).isEqualTo("0.00025000")
        assertThat(reinvestment["quote"].asText()).isEqualTo("400000.00")
        assertThat(reinvestment["amount"].asText()).isEqualTo("100.00")
    }

    @Test
    fun `lines of another account never show up`() {
        val bia = register("bia@example.com")
        insertDeposit(bia, "999.00", clock.now().minusSeconds(60))
        deposit("10.00")

        statement().andExpect {
            status { isOk() }
            jsonPath("$.entries.length()") { value(1) }
            jsonPath("$.entries[0].amount") { value("10.00") }
        }
    }

    @Test
    fun `without a valid token it is 401`() {
        listOf(null, "Bearer ${JwtTestTokens.MALFORMED}", "Bearer ${JwtTestTokens.expired(accountId, clock.now())}")
            .forEach { authorization ->
                mockMvc
                    .get("/api/v1/me/statement") {
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
                    content = """{"name":"Ana Souza","email":"$email","password":"s3nha-forte"}"""
                }.andReturn()
                .response.contentAsString
        return UUID.fromString(objectMapper.readTree(body)["id"].asText())
    }

    private fun toUtc(offsetDateTime: String): String =
        java.time.OffsetDateTime
            .parse(offsetDateTime)
            .toInstant()
            .toString()

    private fun insertDeposit(
        account: UUID,
        amount: String,
        at: Instant,
    ) {
        jdbcTemplate.update(
            "INSERT INTO transactions (id, account_id, type, amount, occurred_at) " +
                "VALUES (?, ?, 'DEPOSIT', ?::numeric, ?)",
            UUID.randomUUID(),
            account,
            amount,
            Timestamp.from(at),
        )
    }

    private fun deposit(amount: String) = post("/api/v1/me/deposits", amount)

    private fun purchase(amount: String) = post("/api/v1/me/purchases", amount)

    private fun redeem(amount: String) = post("/api/v1/me/redemptions", amount)

    private fun post(
        path: String,
        amount: String,
    ) {
        mockMvc
            .post(path) {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.valid(accountId, clock.now())}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"amount":"$amount"}"""
            }.andExpect { status { isCreated() } }
    }

    private fun statement(query: String = ""): ResultActionsDsl =
        mockMvc.get("/api/v1/me/statement$query") {
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
