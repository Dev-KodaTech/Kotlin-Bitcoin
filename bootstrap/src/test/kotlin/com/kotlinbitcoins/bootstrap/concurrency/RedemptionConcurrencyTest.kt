package com.kotlinbitcoins.bootstrap.concurrency

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
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * FR-034, SC-005: concurrent redemptions never liquidate the same satoshi twice. One investment of 0.00200000 BTC
 * bought at 400000.00 is worth 800.00 at a buy price of 400000.00, and each 100.00 corresponds to exactly 25_000
 * satoshis, with no rounding residue. The account row lock serialises the ten redemptions, so exactly eight succeed.
 */
class RedemptionConcurrencyTest : ApplicationTestBase() {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var accountId: UUID

    @BeforeEach
    fun registerAccountAndBuyTheInvestment() {
        quoteSource.stubFor(
            get(urlEqualTo("/api/BTC/ticker/")).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"ticker":{"buy":"400000.00","sell":"400000.00","date":1762686600}}"""),
            ),
        )
        val body =
            mockMvc
                .post("/api/v1/accounts") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"Ana Souza","email":"ana@example.com","password":"s3nha-forte"}"""
                }.andReturn()
                .response.contentAsString
        accountId = UUID.fromString(objectMapper.readTree(body)["id"].asText())
        post("/api/v1/me/deposits", """{"amount":"800.00"}""")
        post("/api/v1/me/purchases", """{"amount":"800.00"}""")
    }

    @Test
    fun `ten parallel redemptions of 100 against an investment worth 800 yield eight successes and two refusals`() {
        val attempts = 10
        val pool = Executors.newFixedThreadPool(attempts)
        val startSignal = CountDownLatch(1)
        try {
            val futures =
                (1..attempts).map {
                    pool.submit<Pair<Int, String?>> {
                        startSignal.await()
                        val response = post("/api/v1/me/redemptions", """{"amount":"100.00"}""")
                        response.status to objectMapper.readTree(response.contentAsString)["code"]?.asText()
                    }
                }
            startSignal.countDown()
            val results = futures.map { it.get(60, TimeUnit.SECONDS) }

            assertThat(results.count { it.first == 201 }).isEqualTo(8)
            assertThat(results.filter { it.first != 201 }).hasSize(2).allSatisfy {
                assertThat(it.first).isEqualTo(422)
                assertThat(it.second).isEqualTo("REDEMPTION_EXCEEDS_AVAILABLE")
            }
        } finally {
            pool.shutdownNow()
        }

        // Deposits − purchases + redemptions: 800.00 − 800.00 + 8 × 100.00.
        val balance =
            jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?",
                BigDecimal::class.java,
                accountId,
            )!!
        assertThat(balance.toPlainString()).isEqualTo("800.00")

        // No satoshi is liquidated twice: what is still active plus what was sold is exactly what was bought.
        val purchased = sum("SELECT COALESCE(SUM(btc_amount_sats), 0) FROM transactions WHERE type = 'PURCHASE'")
        val sold = sum("SELECT COALESCE(SUM(btc_amount_sats), 0) FROM transactions WHERE type = 'REDEMPTION'")
        val active = sum("SELECT COALESCE(SUM(btc_amount_sats), 0) FROM investments WHERE status = 'ACTIVE'")
        assertThat(purchased).isEqualTo(200_000L)
        assertThat(sold).isEqualTo(200_000L)
        assertThat(active).isZero()
        assertThat(purchased).isEqualTo(active + sold)
        assertThat(count("transactions WHERE type = 'REDEMPTION'")).isEqualTo(8)
        assertThat(count("investments WHERE status = 'ACTIVE'")).isZero()
    }

    private fun post(
        path: String,
        body: String,
    ) = mockMvc
        .post(path) {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.valid(accountId, clock.now())}")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andReturn()
        .response

    private fun sum(sql: String): Long = jdbcTemplate.queryForObject(sql, Long::class.java)!!

    private fun count(table: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)!!
}
