package com.kotlinbitcoins.bootstrap.contract

import com.fasterxml.jackson.databind.ObjectMapper
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
import java.util.UUID

/**
 * US3 acceptance scenarios 1–2 over the assembled application, in what depends only on deposits
 * (contracts/openapi.yaml: /me/balance). The token comes from the real login endpoint, which also closes the
 * independent test of US1: a valid token works on a protected route.
 */
class BalanceContractTest : ApplicationTestBase() {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var accountId: UUID

    @BeforeEach
    fun registerAccount() {
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
    fun `scenario 1 - a new account has a zero balance, read with the token from the real login`() {
        val token = login()

        balance("Bearer $token").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.balance") { value("0.00") }
        }
    }

    @Test
    fun `scenario 2 - the balance reflects the deposits made`() {
        val token = login()

        deposit(token, "100.00").andExpect { status { isCreated() } }
        deposit(token, "50.50").andExpect { status { isCreated() } }

        balance("Bearer $token").andExpect {
            status { isOk() }
            jsonPath("$.balance") { value("150.50") }
        }
    }

    @Test
    fun `a balance query changes nothing`() {
        val token = login()
        deposit(token, "100.00").andExpect { status { isCreated() } }

        repeat(3) { balance("Bearer $token").andExpect { jsonPath("$.balance") { value("100.00") } } }

        val transactions = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Int::class.java)
        org.assertj.core.api.Assertions
            .assertThat(transactions)
            .isEqualTo(1)
    }

    @Test
    fun `without a valid token the balance is 401`() {
        listOf(null, "Bearer ${JwtTestTokens.MALFORMED}", "Bearer ${JwtTestTokens.expired(accountId, clock.now())}")
            .forEach { authorization ->
                balance(authorization).andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.code") { value("UNAUTHORIZED") }
                }
            }
    }

    @Test
    fun `a token for an account that does not exist is 401`() {
        balance("Bearer ${JwtTestTokens.valid(UUID.randomUUID(), clock.now())}").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    private fun login(): String {
        val body =
            mockMvc
                .post("/api/v1/auth/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"ana@example.com","password":"s3nha-forte"}"""
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        return objectMapper.readTree(body)["accessToken"].asText()
    }

    private fun deposit(
        token: String,
        amount: String,
    ): ResultActionsDsl =
        mockMvc.post("/api/v1/me/deposits") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":"$amount"}"""
        }

    private fun balance(authorization: String?): ResultActionsDsl =
        mockMvc.get("/api/v1/me/balance") {
            authorization?.let { header(HttpHeaders.AUTHORIZATION, it) }
        }
}
