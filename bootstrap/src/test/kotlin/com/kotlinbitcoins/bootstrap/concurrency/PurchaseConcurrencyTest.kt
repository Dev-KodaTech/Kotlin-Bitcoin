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
 * FR-010, FR-034, SC-005: concurrent purchases never overdraw the balance. The account row lock serialises them, so
 * with 100.00 and twenty purchases of 10.00 exactly ten succeed and ten are refused.
 */
class PurchaseConcurrencyTest : ApplicationTestBase() {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var accountId: UUID

    @BeforeEach
    fun registerAccountWithBalance() {
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
        mockMvc
            .post("/api/v1/me/deposits") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.valid(accountId, clock.now())}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"amount":"100.00"}"""
            }.andReturn()
    }

    @Test
    fun `twenty parallel purchases of 10 against a balance of 100 yield ten successes and ten refusals`() {
        val attempts = 20
        val pool = Executors.newFixedThreadPool(attempts)
        val startSignal = CountDownLatch(1)
        try {
            val futures =
                (1..attempts).map {
                    pool.submit<Pair<Int, String?>> {
                        startSignal.await()
                        val response =
                            mockMvc
                                .post("/api/v1/me/purchases") {
                                    header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer ${JwtTestTokens.valid(accountId, clock.now())}",
                                    )
                                    contentType = MediaType.APPLICATION_JSON
                                    content = """{"amount":"10.00"}"""
                                }.andReturn()
                                .response
                        response.status to objectMapper.readTree(response.contentAsString)["code"]?.asText()
                    }
                }
            startSignal.countDown()
            val results = futures.map { it.get(60, TimeUnit.SECONDS) }

            assertThat(results.count { it.first == 201 }).isEqualTo(10)
            assertThat(results.filter { it.first != 201 }).hasSize(10).allSatisfy {
                assertThat(it.first).isEqualTo(422)
                assertThat(it.second).isEqualTo("INSUFFICIENT_BALANCE")
            }
        } finally {
            pool.shutdownNow()
        }

        val balance =
            jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?",
                BigDecimal::class.java,
                accountId,
            )!!
        assertThat(balance.toPlainString()).isEqualTo("0.00")
        assertThat(count("investments")).isEqualTo(10)
        assertThat(count("transactions WHERE type = 'PURCHASE'")).isEqualTo(10)
        val distinctFifo =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT fifo_sequence) FROM investments",
                Int::class.java,
            )
        assertThat(distinctFifo).isEqualTo(10)
    }

    private fun count(table: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)!!
}
