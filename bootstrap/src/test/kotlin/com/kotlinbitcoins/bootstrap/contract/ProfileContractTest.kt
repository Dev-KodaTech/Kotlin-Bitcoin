package com.kotlinbitcoins.bootstrap.contract

import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import com.kotlinbitcoins.bootstrap.support.JwtTestTokens
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import java.util.UUID

/**
 * US3 acceptance scenario 3 over the assembled application (FR-036, contracts/openapi.yaml: /me): the profile has
 * only name and e-mail of the account of the token.
 */
class ProfileContractTest : ApplicationTestBase() {
    private lateinit var accountId: UUID

    @BeforeEach
    fun registerAccount() {
        accountId = api.register("Maria Souza", "maria@example.com")
        api.depositOk(accountId, "1234.56")
    }

    @Test
    fun `scenario 3 - the profile has only the name and the e-mail of the account`() {
        api.get("/api/v1/me", accountId).andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            content { json("""{"name":"Maria Souza","email":"maria@example.com"}""", strict = true) }
        }
    }

    @Test
    fun `without a valid token the profile is 401`() {
        listOf(null, "Bearer ${JwtTestTokens.MALFORMED}", "Bearer ${JwtTestTokens.expired(accountId, clock.now())}")
            .forEach { authorization ->
                profile(authorization).andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.code") { value("UNAUTHORIZED") }
                }
            }
    }

    @Test
    fun `a token for an account that does not exist is 401`() {
        profile("Bearer ${JwtTestTokens.valid(UUID.randomUUID(), clock.now())}").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    private fun profile(authorization: String?): ResultActionsDsl =
        mockMvc.get("/api/v1/me") {
            authorization?.let { header(HttpHeaders.AUTHORIZATION, it) }
        }
}
