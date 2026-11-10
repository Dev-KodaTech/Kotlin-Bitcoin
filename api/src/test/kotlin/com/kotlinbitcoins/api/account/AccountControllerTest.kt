package com.kotlinbitcoins.api.account

import com.kotlinbitcoins.api.support.WebMvcTestConfiguration
import com.kotlinbitcoins.application.account.FieldError
import com.kotlinbitcoins.application.account.RegisterAccount
import com.kotlinbitcoins.application.account.RegisterAccountCommand
import com.kotlinbitcoins.application.account.RegisterAccountError
import com.kotlinbitcoins.application.account.RegisteredAccount
import com.kotlinbitcoins.application.account.Violation
import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [AccountController::class])
@Import(WebMvcTestConfiguration::class)
class AccountControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var registerAccount: RegisterAccount

    private val accountId = AccountId(UUID.fromString("7f8a1c2e-1111-4222-8333-944455556666"))

    @Test
    fun `registration answers 201 with the account and no password`() {
        every { registerAccount.execute(any()) } returns Outcome.Success(registered())

        register("""{"name":"Ana Souza","email":"ana@example.com","password":"s3nha-forte"}""")
            .andExpect {
                status { isCreated() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.id") { value(accountId.toString()) }
                jsonPath("$.name") { value("Ana Souza") }
                jsonPath("$.email") { value("ana@example.com") }
                jsonPath("$.balance") { value("0.00") }
                jsonPath("$.createdAt") { value("2026-11-09T08:10:00-03:00") }
                jsonPath("$.password") { doesNotExist() }
                jsonPath("$.passwordHash") { doesNotExist() }
                content { string(not(containsString("s3nha-forte"))) }
            }
        verify { registerAccount.execute(RegisterAccountCommand("Ana Souza", "ana@example.com", "s3nha-forte")) }
    }

    @Test
    fun `invalid input from the use case is VALIDATION_ERROR listing the fields`() {
        every { registerAccount.execute(any()) } returns
            Outcome.Failure(
                RegisterAccountError.InvalidInput(
                    listOf(FieldError("email", Violation.INVALID_FORMAT), FieldError("password", Violation.TOO_LONG)),
                ),
            )

        register("""{"name":"Ana","email":"ana@","password":"${"é".repeat(37)}"}""")
            .andExpectProblem(400, "VALIDATION_ERROR")
            .andExpect {
                jsonPath("$.errors.length()") { value(2) }
                jsonPath("$.errors[0].field") { value("email") }
                jsonPath("$.errors[0].message") { value("E-mail em formato inválido.") }
                jsonPath("$.errors[1].field") { value("password") }
                jsonPath("$.errors[1].message") { value("A senha deve ter no máximo 72 bytes.") }
            }
    }

    @Test
    fun `missing or blank fields are VALIDATION_ERROR before reaching the use case`() {
        register("""{"name":" ","email":"","password":"1234567"}""")
            .andExpectProblem(400, "VALIDATION_ERROR")
            .andExpect {
                jsonPath("$.errors[?(@.field == 'name')]") { isNotEmpty() }
                jsonPath("$.errors[?(@.field == 'email')]") { isNotEmpty() }
                jsonPath("$.errors[?(@.field == 'password')]") { isNotEmpty() }
            }
        register("{}").andExpectProblem(400, "VALIDATION_ERROR")
        verify(exactly = 0) { registerAccount.execute(any()) }
    }

    @Test
    fun `e-mail already in use is 409 EMAIL_ALREADY_IN_USE`() {
        every { registerAccount.execute(any()) } returns Outcome.Failure(RegisterAccountError.EmailAlreadyInUse)

        register("""{"name":"Ana","email":"ana@example.com","password":"s3nha-forte"}""")
            .andExpectProblem(409, "EMAIL_ALREADY_IN_USE")
            .andExpect { jsonPath("$.detail") { value("Este e-mail já está em uso.") } }
    }

    @Test
    fun `infrastructure failure is 503 SERVICE_UNAVAILABLE`() {
        every { registerAccount.execute(any()) } returns Outcome.Failure(RegisterAccountError.ServiceUnavailable)

        register("""{"name":"Ana","email":"ana@example.com","password":"s3nha-forte"}""")
            .andExpectProblem(503, "SERVICE_UNAVAILABLE")
    }

    private fun register(body: String): ResultActionsDsl =
        mockMvc.post("/api/v1/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun ResultActionsDsl.andExpectProblem(
        status: Int,
        code: String,
    ): ResultActionsDsl =
        andExpect {
            status { isEqualTo(status) }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value(code) }
            jsonPath("$.status") { value(status) }
            jsonPath("$.detail") { isNotEmpty() }
        }

    private fun registered() =
        RegisteredAccount(
            id = accountId,
            name = "Ana Souza",
            email = (Email.of("ana@example.com") as Outcome.Success).value,
            balance = Money.ZERO,
            createdAt = Instant.parse("2026-11-09T11:10:00Z"),
        )
}
