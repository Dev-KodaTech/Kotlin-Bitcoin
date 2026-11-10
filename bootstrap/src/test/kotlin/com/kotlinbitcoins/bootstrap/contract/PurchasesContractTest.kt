package com.kotlinbitcoins.bootstrap.contract

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

/** US5 acceptance scenarios 1–5 over the assembled application (contracts/openapi.yaml: /me/purchases). */
class PurchasesContractTest : ApplicationTestBase() {
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
    fun `scenario 1 - buying 400 at a sell price of 400000 debits the balance and creates 0_001 BTC`() {
        deposit("1000.00")

        purchase("400.00")
            .andExpect {
                status { isCreated() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.investmentId") { isNotEmpty() }
                jsonPath("$.transactionId") { isNotEmpty() }
                jsonPath("$.amount") { value("400.00") }
                jsonPath("$.btcAmount") { value("0.00100000") }
                jsonPath("$.quote") { value("400000.00") }
                jsonPath("$.purchasedAt") { value("2026-11-09T08:10:00-03:00") }
                jsonPath("$.balance") { value("600.00") }
            }

        assertThat(balance()).isEqualTo("600.00")
        val investment = jdbcTemplate.queryForMap("SELECT * FROM investments")
        assertThat(investment["account_id"].toString()).isEqualTo(accountId.toString())
        assertThat(investment["btc_amount_sats"]).isEqualTo(100_000L)
        assertThat((investment["invested_amount"] as BigDecimal).toPlainString()).isEqualTo("400.00")
        assertThat(investment["purchase_quote"] as BigDecimal).isEqualByComparingTo(BigDecimal("400000.00"))
        assertThat(investment["status"]).isEqualTo("ACTIVE")
        assertThat(investment["origin"]).isEqualTo("PURCHASE")
        assertThat(investment["origin_investment_id"]).isNull()
    }

    @Test
    fun `the purchase is rounded down to the satoshi`() {
        stubSource(buy = "300000.00", sell = "300000.00")
        deposit("100.00")

        purchase("100.00")
            .andExpect {
                status { isCreated() }
                jsonPath("$.btcAmount") { value("0.00033333") }
                jsonPath("$.balance") { value("0.00") }
            }
    }

    @Test
    fun `scenario 2 - an amount above the balance is 422 and changes nothing`() {
        deposit("100.00")
        val before = emailSender.messages.size

        purchase("100.01").andExpect {
            status { isUnprocessableEntity() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("INSUFFICIENT_BALANCE") }
            jsonPath("$.detail") { value("Saldo insuficiente para esta compra.") }
        }

        assertThat(balance()).isEqualTo("100.00")
        assertThat(count("investments")).isZero()
        assertThat(count("transactions")).isEqualTo(1)
        assertThat(count("outbox_events")).isEqualTo(1)
        assertThat(emailSender.messages).hasSize(before)
    }

    @Test
    fun `scenario 3 - invalid amounts are 400 and change nothing`() {
        deposit("100.00")

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

        assertThat(balance()).isEqualTo("100.00")
        assertThat(count("investments")).isZero()
        assertThat(count("transactions")).isEqualTo(1)
    }

    @Test
    fun `an amount worth less than one satoshi is 400 INVALID_AMOUNT and changes nothing`() {
        stubSource(buy = "2000000.00", sell = "2000000.00")
        deposit("1.00")

        purchase("0.01").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_AMOUNT") }
        }

        assertThat(balance()).isEqualTo("1.00")
        assertThat(count("investments")).isZero()
    }

    @Test
    fun `scenario 4 - with the source down and no quote of 60 seconds or less the purchase is 503 and has no effect`() {
        deposit("1000.00")
        quoteSource.stubFor(get(urlEqualTo(SOURCE_PATH)).willReturn(aResponse().withStatus(521)))

        purchase("400.00").andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("QUOTE_UNAVAILABLE") }
        }

        assertThat(balance()).isEqualTo("1000.00")
        assertThat(count("investments")).isZero()
        assertThat(count("transactions")).isEqualTo(1)
    }

    @Test
    fun `with the source down a quote of 30 seconds is still used for the purchase`() {
        deposit("1000.00")
        purchase("100.00").andExpect { status { isCreated() } }
        quoteSource.stubFor(get(urlEqualTo(SOURCE_PATH)).willReturn(aResponse().withStatus(521)))
        clock.advance(Duration.ofSeconds(30))

        purchase("100.00").andExpect {
            status { isCreated() }
            jsonPath("$.quote") { value("400000.00") }
        }
    }

    @Test
    fun `scenario 5 - the PURCHASE line carries the amount, the bitcoin, the sell price and the investment`() {
        deposit("1000.00")

        val response = objectMapper.readTree(purchase("400.00").andReturn().response.contentAsString)

        val line = jdbcTemplate.queryForMap("SELECT * FROM transactions WHERE type = 'PURCHASE'")
        assertThat(line["id"].toString()).isEqualTo(response["transactionId"].asText())
        assertThat(line["account_id"].toString()).isEqualTo(accountId.toString())
        assertThat((line["amount"] as BigDecimal).toPlainString()).isEqualTo("400.00")
        assertThat(line["btc_amount_sats"]).isEqualTo(100_000L)
        assertThat(line["quote"] as BigDecimal).isEqualByComparingTo(BigDecimal("400000.00"))
        assertThat(line["investment_id"].toString()).isEqualTo(response["investmentId"].asText())
        assertThat(line["request_id"]).isNull()
    }

    @Test
    fun `the client receives an e-mail with the amount in reais and the bitcoin bought`() {
        deposit("1000.00")
        await().atMost(Duration.ofSeconds(10)).untilAsserted { assertThat(emailSender.messages).hasSize(1) }

        purchase("400.00").andExpect { status { isCreated() } }

        await().atMost(Duration.ofSeconds(10)).untilAsserted { assertThat(emailSender.messages).hasSize(2) }
        val message = emailSender.messages.last()
        assertThat(message.recipientEmail.value).isEqualTo("ana@example.com")
        assertThat(message.content).isEqualTo(EmailContent.PurchaseConfirmed(money("400.00"), BtcAmount(100_000)))
    }

    @Test
    fun `FR-031 - an e-mail provider that is down never undoes the purchase`() {
        deposit("1000.00")
        emailSender.isFailing = true

        purchase("400.00").andExpect {
            status { isCreated() }
            jsonPath("$.balance") { value("600.00") }
        }

        assertThat(balance()).isEqualTo("600.00")
        assertThat(count("investments")).isEqualTo(1)
    }

    @Test
    fun `without a valid token the purchase is 401 and changes nothing`() {
        deposit("1000.00")

        listOf(null, "Bearer ${JwtTestTokens.MALFORMED}", "Bearer ${JwtTestTokens.expired(accountId, clock.now())}")
            .forEach { authorization ->
                mockMvc
                    .post("/api/v1/me/purchases") {
                        authorization?.let { header(HttpHeaders.AUTHORIZATION, it) }
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"amount":"400.00"}"""
                    }.andExpect {
                        status { isUnauthorized() }
                        jsonPath("$.code") { value("UNAUTHORIZED") }
                    }
            }

        assertThat(balance()).isEqualTo("1000.00")
        assertThat(count("investments")).isZero()
    }

    @Test
    fun `a token for an account that does not exist is 401`() {
        mockMvc
            .post("/api/v1/me/purchases") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.valid(UUID.randomUUID(), clock.now())}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"amount":"400.00"}"""
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("UNAUTHORIZED") }
            }
    }

    private fun deposit(amount: String) {
        mockMvc
            .post("/api/v1/me/deposits") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.valid(accountId, clock.now())}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"amount":"$amount"}"""
            }.andExpect { status { isCreated() } }
    }

    private fun purchase(amount: String): ResultActionsDsl = post("""{"amount":"$amount"}""")

    private fun post(body: String): ResultActionsDsl =
        mockMvc.post("/api/v1/me/purchases") {
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

    private fun count(table: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)!!

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value

    private companion object {
        const val SOURCE_PATH = "/api/BTC/ticker/"
    }
}
