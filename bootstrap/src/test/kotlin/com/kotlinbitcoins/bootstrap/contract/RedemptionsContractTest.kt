package com.kotlinbitcoins.bootstrap.contract

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.kotlinbitcoins.application.port.EmailContent
import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import com.kotlinbitcoins.bootstrap.support.JwtTestTokens
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

/**
 * US7 acceptance scenarios 1–9 and the walkthrough of quickstart steps 12–14, over the assembled application
 * (contracts/openapi.yaml: /me/redemptions).
 *
 * Investment A (0.00100000 BTC) and B (0.00050000 BTC) are bought at 400000.00 five minutes apart; the buy price then
 * moves to 200000.00, so A is worth 200.00 and B 100.00.
 */
class RedemptionsContractTest : ApplicationTestBase() {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var accountId: UUID
    private lateinit var investmentA: String
    private lateinit var investmentB: String

    @BeforeEach
    fun registerAccountAndStubQuote() {
        stubSource(buy = "400000.00", sell = "400000.00")
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
    fun `quickstart 12 to 14 - 250 consumes A whole and B partly and reinvests the residue of B`() {
        buyAAndBThenDropTheBuyPriceTo200000()

        redeem("250.00")
            .andExpect {
                status { isCreated() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.requestId") { isNotEmpty() }
                jsonPath("$.amount") { value("250.00") }
                jsonPath("$.btcSold") { value("0.00125000") }
                jsonPath("$.quote") { value("200000.00") }
                jsonPath("$.balance") { value("650.00") }
                jsonPath("$.occurredAt") { value("2026-11-09T08:15:11-03:00") }
                jsonPath("$.liquidations.length()") { value(2) }
                jsonPath("$.liquidations[0].investmentId") { value(investmentA) }
                jsonPath("$.liquidations[0].amount") { value("200.00") }
                jsonPath("$.liquidations[0].btcAmount") { value("0.00100000") }
                jsonPath("$.liquidations[0].isPartial") { value(false) }
                jsonPath("$.liquidations[1].investmentId") { value(investmentB) }
                jsonPath("$.liquidations[1].amount") { value("50.00") }
                jsonPath("$.liquidations[1].btcAmount") { value("0.00025000") }
                jsonPath("$.liquidations[1].isPartial") { value(true) }
                jsonPath("$.reinvestment.investmentId") { isNotEmpty() }
                jsonPath("$.reinvestment.btcAmount") { value("0.00025000") }
                jsonPath("$.reinvestment.investedAmount") { value("100.00") }
                jsonPath("$.reinvestment.purchaseQuote") { value("400000.00") }
                jsonPath("$.reinvestment.purchasedAt") { value("2026-11-09T08:15:00-03:00") }
            }

        assertThat(balance()).isEqualTo("650.00")
        assertThat(status(investmentA)).isEqualTo("CLOSED")
        assertThat(status(investmentB)).isEqualTo("CLOSED")
        val reinvestment = jdbcTemplate.queryForMap("SELECT * FROM investments WHERE origin = 'REINVESTMENT'")
        assertThat(reinvestment["status"]).isEqualTo("ACTIVE")
        assertThat(reinvestment["origin_investment_id"].toString()).isEqualTo(investmentB)
        assertThat(reinvestment["btc_amount_sats"]).isEqualTo(25_000L)
        assertThat((reinvestment["invested_amount"] as BigDecimal).toPlainString()).isEqualTo("100.00")
        assertThat(reinvestment["purchase_quote"] as BigDecimal).isEqualByComparingTo(BigDecimal("400000.00"))
        val original = jdbcTemplate.queryForMap("SELECT * FROM investments WHERE id = ?", UUID.fromString(investmentB))
        assertThat(reinvestment["purchased_at"]).isEqualTo(original["purchased_at"])
        assertThat(reinvestment["fifo_sequence"]).isEqualTo(original["fifo_sequence"])

        // What is left (0.00025000 BTC) is worth 50.00, so 50.01 is refused and nothing changes.
        val investmentsBefore = count("investments")
        val linesBefore = count("transactions")
        redeem("50.01").andExpect {
            status { isUnprocessableEntity() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("REDEMPTION_EXCEEDS_AVAILABLE") }
            jsonPath("$.detail") { value("O valor solicitado excede o valor atual dos seus investimentos.") }
        }
        assertThat(balance()).isEqualTo("650.00")
        assertThat(count("investments")).isEqualTo(investmentsBefore)
        assertThat(count("transactions")).isEqualTo(linesBefore)
    }

    @Test
    fun `scenario 1 - a request equal to A liquidates A whole and leaves B intact`() {
        buyAAndBThenDropTheBuyPriceTo200000()

        redeem("200.00").andExpect {
            status { isCreated() }
            jsonPath("$.balance") { value("600.00") }
            jsonPath("$.btcSold") { value("0.00100000") }
            jsonPath("$.liquidations.length()") { value(1) }
            jsonPath("$.liquidations[0].investmentId") { value(investmentA) }
            jsonPath("$.liquidations[0].isPartial") { value(false) }
            jsonPath("$.reinvestment") { value(null) }
        }

        assertThat(status(investmentA)).isEqualTo("CLOSED")
        assertThat(status(investmentB)).isEqualTo("ACTIVE")
        assertThat(count("investments")).isEqualTo(2)
        assertThat(count("transactions WHERE type = 'REINVESTMENT'")).isZero()
    }

    @Test
    fun `a request equal to the total liquidates everything with no reinvestment`() {
        buyAAndBThenDropTheBuyPriceTo200000()

        redeem("300.00").andExpect {
            status { isCreated() }
            jsonPath("$.balance") { value("700.00") }
            jsonPath("$.liquidations.length()") { value(2) }
            jsonPath("$.reinvestment") { value(null) }
        }

        assertThat(count("investments WHERE status = 'ACTIVE'")).isZero()
    }

    @Test
    fun `scenario 3 - redeeming 50000 of 1 BTC bought at 100000 sells a quarter and reinvests three quarters`() {
        stubSource(buy = "100000.00", sell = "100000.00")
        deposit("100000.00")
        purchase("100000.00")
        stubSource(buy = "200000.00", sell = "200000.00")
        clock.advance(Duration.ofSeconds(11))

        redeem("50000.00").andExpect {
            status { isCreated() }
            jsonPath("$.btcSold") { value("0.25000000") }
            jsonPath("$.balance") { value("50000.00") }
            jsonPath("$.reinvestment.btcAmount") { value("0.75000000") }
            jsonPath("$.reinvestment.purchaseQuote") { value("100000.00") }
            jsonPath("$.reinvestment.investedAmount") { value("75000.00") }
            jsonPath("$.reinvestment.purchasedAt") { value("2026-11-09T08:10:00-03:00") }
        }
    }

    @Test
    fun `scenario 4 - a reinvestment is liquidated before an investment bought after the original`() {
        buyAAndBThenDropTheBuyPriceTo200000()
        val first = objectMapper.readTree(redeem("50.00").andReturn().response.contentAsString)
        val reinvestmentId = first["reinvestment"]["investmentId"].asText()
        stubSource(buy = "200000.00", sell = "400000.00")
        clock.advance(Duration.ofMinutes(10))
        purchase("100.00")

        redeem("20.00").andExpect {
            status { isCreated() }
            jsonPath("$.liquidations.length()") { value(1) }
            jsonPath("$.liquidations[0].investmentId") { value(reinvestmentId) }
            jsonPath("$.liquidations[0].isPartial") { value(true) }
        }
    }

    @Test
    fun `scenario 5 - the statement lines of a redemption share the request id and carry their quotes`() {
        buyAAndBThenDropTheBuyPriceTo200000()

        val response = objectMapper.readTree(redeem("250.00").andReturn().response.contentAsString)

        val requestId = UUID.fromString(response["requestId"].asText())
        val lines =
            jdbcTemplate.queryForList(
                "SELECT * FROM transactions WHERE request_id = ? ORDER BY type",
                requestId,
            )
        assertThat(lines).hasSize(3)
        val redemptions = lines.filter { it["type"] == "REDEMPTION" }
        val reinvestments = lines.filter { it["type"] == "REINVESTMENT" }
        assertThat(redemptions).hasSize(2)
        assertThat(reinvestments).hasSize(1)

        val lineA = redemptions.single { it["investment_id"].toString() == investmentA }
        assertThat((lineA["amount"] as BigDecimal).toPlainString()).isEqualTo("200.00")
        assertThat(lineA["btc_amount_sats"]).isEqualTo(100_000L)
        assertThat(lineA["quote"] as BigDecimal).isEqualByComparingTo(BigDecimal("200000.00"))
        val lineB = redemptions.single { it["investment_id"].toString() == investmentB }
        assertThat((lineB["amount"] as BigDecimal).toPlainString()).isEqualTo("50.00")
        assertThat(lineB["btc_amount_sats"]).isEqualTo(25_000L)
        assertThat(lineB["quote"] as BigDecimal).isEqualByComparingTo(BigDecimal("200000.00"))

        val reinvested = reinvestments.single()
        assertThat((reinvested["amount"] as BigDecimal).toPlainString()).isEqualTo("100.00")
        assertThat(reinvested["btc_amount_sats"]).isEqualTo(25_000L)
        assertThat(reinvested["quote"] as BigDecimal).isEqualByComparingTo(BigDecimal("400000.00"))
        assertThat(reinvested["investment_id"].toString()).isEqualTo(response["reinvestment"]["investmentId"].asText())
        assertThat(redemptions.sumOf { it["amount"] as BigDecimal }).isEqualByComparingTo(BigDecimal("250.00"))
    }

    @Test
    fun `scenario 6 - an amount above the current value is 422 and has no effect`() {
        buyAAndBThenDropTheBuyPriceTo200000()
        val emailsBefore = emailSender.messages.size

        redeem("300.01").andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("REDEMPTION_EXCEEDS_AVAILABLE") }
        }

        assertThat(balance()).isEqualTo("400.00")
        assertThat(status(investmentA)).isEqualTo("ACTIVE")
        assertThat(status(investmentB)).isEqualTo("ACTIVE")
        assertThat(count("investments")).isEqualTo(2)
        assertThat(count("transactions WHERE type IN ('REDEMPTION', 'REINVESTMENT')")).isZero()
        assertThat(emailSender.messages).hasSize(emailsBefore)
    }

    @Test
    fun `scenario 7 - an account without investments is refused the same way`() {
        redeem("1.00").andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("REDEMPTION_EXCEEDS_AVAILABLE") }
        }

        assertThat(count("transactions")).isZero()
    }

    @Test
    fun `scenario 8 - invalid amounts are 400 and change nothing`() {
        buyAAndBThenDropTheBuyPriceTo200000()

        listOf("0", "0.00", "-5", "abc", "10.123", "1e3", "").forEach { amount ->
            post("""{"amount":"$amount"}""").andExpect {
                status { isBadRequest() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("INVALID_AMOUNT") }
            }
        }
        post("{}").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_AMOUNT") }
        }

        assertThat(balance()).isEqualTo("400.00")
        assertThat(count("transactions WHERE type IN ('REDEMPTION', 'REINVESTMENT')")).isZero()
    }

    @Test
    fun `scenario 8 - with the source down and no quote of 60 seconds or less it is 503 and has no effect`() {
        buyAAndBThenDropTheBuyPriceTo200000()
        quoteSource.stubFor(get(urlEqualTo(SOURCE_PATH)).willReturn(aResponse().withStatus(521)))
        clock.advance(Duration.ofSeconds(120))

        redeem("100.00").andExpect {
            status { isServiceUnavailable() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("QUOTE_UNAVAILABLE") }
        }

        assertThat(balance()).isEqualTo("400.00")
        assertThat(status(investmentA)).isEqualTo("ACTIVE")
        assertThat(count("transactions WHERE type IN ('REDEMPTION', 'REINVESTMENT')")).isZero()
    }

    @Test
    fun `scenario 9 - the client receives an e-mail with the amount redeemed and the bitcoin sold`() {
        buyAAndBThenDropTheBuyPriceTo200000()

        redeem("250.00").andExpect { status { isCreated() } }

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val redemptionEmails = emailSender.messages.filter { it.content is EmailContent.RedemptionConfirmed }
            assertThat(redemptionEmails).hasSize(1)
            assertThat(redemptionEmails.single().recipientEmail.value).isEqualTo("ana@example.com")
            assertThat(redemptionEmails.single().content)
                .isEqualTo(EmailContent.RedemptionConfirmed(money("250.00"), BtcAmount(125_000)))
        }
    }

    @Test
    fun `FR-031 - an e-mail provider that is down never undoes the redemption`() {
        buyAAndBThenDropTheBuyPriceTo200000()
        emailSender.isFailing = true

        redeem("250.00").andExpect {
            status { isCreated() }
            jsonPath("$.balance") { value("650.00") }
        }

        assertThat(balance()).isEqualTo("650.00")
    }

    @Test
    fun `without a valid token the redemption is 401 and changes nothing`() {
        buyAAndBThenDropTheBuyPriceTo200000()

        listOf(null, "Bearer ${JwtTestTokens.MALFORMED}", "Bearer ${JwtTestTokens.expired(accountId, clock.now())}")
            .forEach { authorization ->
                mockMvc
                    .post("/api/v1/me/redemptions") {
                        authorization?.let { header(HttpHeaders.AUTHORIZATION, it) }
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"amount":"100.00"}"""
                    }.andExpect {
                        status { isUnauthorized() }
                        jsonPath("$.code") { value("UNAUTHORIZED") }
                    }
            }

        assertThat(balance()).isEqualTo("400.00")
        assertThat(count("transactions WHERE type IN ('REDEMPTION', 'REINVESTMENT')")).isZero()
    }

    @Test
    fun `a token for an account that does not exist is 401`() {
        mockMvc
            .post("/api/v1/me/redemptions") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.valid(UUID.randomUUID(), clock.now())}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"amount":"100.00"}"""
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("UNAUTHORIZED") }
            }
    }

    /** Deposit 1000.00, buy A (400.00) and, 5 minutes later, B (200.00); then the buy price falls to 200000.00. */
    private fun buyAAndBThenDropTheBuyPriceTo200000() {
        deposit("1000.00")
        investmentA = purchase("400.00")["investmentId"].asText()
        clock.advance(Duration.ofMinutes(5))
        investmentB = purchase("200.00")["investmentId"].asText()
        stubSource(buy = "200000.00", sell = "400000.00")
        clock.advance(Duration.ofSeconds(11))
    }

    private fun deposit(amount: String) {
        mockMvc
            .post("/api/v1/me/deposits") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.valid(accountId, clock.now())}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"amount":"$amount"}"""
            }.andExpect { status { isCreated() } }
    }

    private fun purchase(amount: String): JsonNode {
        val response =
            mockMvc
                .post("/api/v1/me/purchases") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.valid(accountId, clock.now())}")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"amount":"$amount"}"""
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        return objectMapper.readTree(response)
    }

    private fun redeem(amount: String): ResultActionsDsl = post("""{"amount":"$amount"}""")

    private fun post(body: String): ResultActionsDsl =
        mockMvc.post("/api/v1/me/redemptions") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.valid(accountId, clock.now())}")
            contentType = MediaType.APPLICATION_JSON
            content = body
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

    private fun balance(): String =
        jdbcTemplate
            .queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal::class.java, accountId)!!
            .toPlainString()

    private fun status(investmentId: String): String =
        jdbcTemplate.queryForObject(
            "SELECT status FROM investments WHERE id = ?",
            String::class.java,
            UUID.fromString(investmentId),
        )!!

    private fun count(table: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)!!

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value

    private companion object {
        const val SOURCE_PATH = "/api/BTC/ticker/"
    }
}
