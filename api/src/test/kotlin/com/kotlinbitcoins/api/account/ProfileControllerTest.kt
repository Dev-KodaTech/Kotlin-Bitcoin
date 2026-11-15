package com.kotlinbitcoins.api.account

import com.kotlinbitcoins.api.support.WebMvcTestConfiguration
import com.kotlinbitcoins.application.account.GetProfile
import com.kotlinbitcoins.application.account.GetProfileError
import com.kotlinbitcoins.application.account.Profile
import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Outcome
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import java.util.UUID

@WebMvcTest(controllers = [ProfileController::class])
@Import(WebMvcTestConfiguration::class)
class ProfileControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var getProfile: GetProfile

    private val accountId = UUID.fromString("7f8a1c2e-1111-4222-8333-944455556666")

    @Test
    fun `profile answers 200 with only the name and the e-mail`() {
        every { getProfile.execute(AccountId(accountId)) } returns
            Outcome.Success(Profile("Maria Souza", (Email.of("maria@example.com") as Outcome.Success).value))

        profile().andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            content { json("""{"name":"Maria Souza","email":"maria@example.com"}""", strict = true) }
            jsonPath("$.id") { doesNotExist() }
            jsonPath("$.balance") { doesNotExist() }
        }
    }

    @Test
    fun `an unknown account is 401 UNAUTHORIZED`() {
        every { getProfile.execute(AccountId(accountId)) } returns Outcome.Failure(GetProfileError.AccountNotFound)

        profile().andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `an infrastructure failure is 503 SERVICE_UNAVAILABLE`() {
        every { getProfile.execute(AccountId(accountId)) } returns Outcome.Failure(GetProfileError.ServiceUnavailable)

        profile().andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("SERVICE_UNAVAILABLE") }
        }
    }

    private fun profile(): ResultActionsDsl =
        mockMvc.get("/api/v1/me") {
            with(jwt().jwt { it.subject(accountId.toString()) })
        }
}
