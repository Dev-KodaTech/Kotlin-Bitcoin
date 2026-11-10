package com.kotlinbitcoins.api.account

import com.kotlinbitcoins.api.support.WebMvcTestConfiguration
import com.kotlinbitcoins.application.account.AuthenticateAccount
import com.kotlinbitcoins.application.account.LoginCommand
import com.kotlinbitcoins.application.account.LoginError
import com.kotlinbitcoins.application.port.AccessToken
import com.kotlinbitcoins.domain.shared.Outcome
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post

@WebMvcTest(controllers = [AuthController::class])
@Import(WebMvcTestConfiguration::class)
class AuthControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var authenticateAccount: AuthenticateAccount

    @Test
    fun `login answers 200 with a bearer token`() {
        every { authenticateAccount.execute(any()) } returns Outcome.Success(AccessToken("jwt-token", 3600))

        login("""{"email":"ana@example.com","password":"s3nha-forte"}""")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.accessToken") { value("jwt-token") }
                jsonPath("$.tokenType") { value("Bearer") }
                jsonPath("$.expiresIn") { value(3600) }
            }
        verify { authenticateAccount.execute(LoginCommand("ana@example.com", "s3nha-forte")) }
    }

    @Test
    fun `invalid credentials are 401 INVALID_CREDENTIALS`() {
        every { authenticateAccount.execute(any()) } returns Outcome.Failure(LoginError.InvalidCredentials)

        login("""{"email":"ana@example.com","password":"wrong-password"}""")
            .andExpect {
                status { isUnauthorized() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("INVALID_CREDENTIALS") }
                jsonPath("$.detail") { value("E-mail ou senha inválidos.") }
            }
    }

    @Test
    fun `missing body is 400 VALIDATION_ERROR`() {
        mockMvc
            .post("/api/v1/auth/login") { contentType = MediaType.APPLICATION_JSON }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
            }
        login("{}").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
        }
        verify(exactly = 0) { authenticateAccount.execute(any()) }
    }

    @Test
    fun `infrastructure failure is 503 SERVICE_UNAVAILABLE`() {
        every { authenticateAccount.execute(any()) } returns Outcome.Failure(LoginError.ServiceUnavailable)

        login("""{"email":"ana@example.com","password":"s3nha-forte"}""")
            .andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.code") { value("SERVICE_UNAVAILABLE") }
            }
    }

    private fun login(body: String): ResultActionsDsl =
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }
}
