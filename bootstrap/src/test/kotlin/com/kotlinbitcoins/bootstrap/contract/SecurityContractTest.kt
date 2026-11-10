package com.kotlinbitcoins.bootstrap.contract

import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import com.kotlinbitcoins.bootstrap.support.JwtTestTokens
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.util.UUID

class SecurityContractTest : ApplicationTestBase() {
    private val accountId = UUID.randomUUID()

    @Test
    fun `protected route without token is UNAUTHORIZED`() {
        mockMvc.get(BALANCE).andExpectUnauthorized()
    }

    @Test
    fun `protected route with malformed token is UNAUTHORIZED`() {
        mockMvc.get(BALANCE) { bearer(JwtTestTokens.MALFORMED) }.andExpectUnauthorized()
    }

    @Test
    fun `protected route with tampered token is UNAUTHORIZED`() {
        mockMvc.get(BALANCE) { bearer(JwtTestTokens.tampered(accountId, clock.now())) }.andExpectUnauthorized()
    }

    @Test
    fun `protected route with expired token is UNAUTHORIZED`() {
        mockMvc.get(BALANCE) { bearer(JwtTestTokens.expired(accountId, clock.now())) }.andExpectUnauthorized()
    }

    @Test
    fun `token expiry is checked against the application clock`() {
        val token = JwtTestTokens.valid(accountId, clock.now())

        // The quote route needs a valid token but not an existing account (the balance route answers 401 for an
        // unknown account), so a non-401 here can only mean the token itself was accepted.
        assertThat(statusOf(mockMvc.get(QUOTE) { bearer(token) })).isNotEqualTo(UNAUTHORIZED)
        clock.advance(Duration.ofHours(2))
        mockMvc.get(QUOTE) { bearer(token) }.andExpectUnauthorized()
    }

    @Test
    fun `registration and login do not require a token`() {
        val register =
            mockMvc.post("/api/v1/accounts") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }
        val login =
            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }

        assertThat(statusOf(register)).isNotEqualTo(UNAUTHORIZED)
        assertThat(statusOf(login)).isNotEqualTo(UNAUTHORIZED)
    }

    @Test
    fun `health endpoint is public`() {
        mockMvc.get("/actuator/health").andExpect { status { isOk() } }
        mockMvc.get("/actuator/health/readiness").andExpect { status { isOk() } }
    }

    private fun MockHttpServletRequestDsl.bearer(token: String) {
        header(HttpHeaders.AUTHORIZATION, "Bearer $token")
    }

    private fun statusOf(result: ResultActionsDsl): Int = result.andReturn().response.status

    private fun ResultActionsDsl.andExpectUnauthorized() {
        andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("about:blank") }
            jsonPath("$.title") { value("Unauthorized") }
            jsonPath("$.status") { value(UNAUTHORIZED) }
            jsonPath("$.code") { value("UNAUTHORIZED") }
            jsonPath("$.detail") { value("Autenticação necessária.") }
            jsonPath("$.correlationId") { isNotEmpty() }
            header { exists("X-Correlation-Id") }
        }
    }

    private companion object {
        const val BALANCE = "/api/v1/me/balance"
        const val QUOTE = "/api/v1/quotes/current"
        const val UNAUTHORIZED = 401
    }
}
