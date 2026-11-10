package com.kotlinbitcoins.api.account

import com.kotlinbitcoins.api.support.WebMvcTestConfiguration
import com.kotlinbitcoins.application.account.DepositCommand
import com.kotlinbitcoins.application.account.DepositError
import com.kotlinbitcoins.application.account.DepositFunds
import com.kotlinbitcoins.application.account.DepositReceipt
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import com.kotlinbitcoins.domain.shared.TransactionId
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [DepositController::class])
@Import(WebMvcTestConfiguration::class)
class DepositControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var depositFunds: DepositFunds

    private val accountId = UUID.fromString("7f8a1c2e-1111-4222-8333-944455556666")
    private val transactionId = TransactionId(UUID.fromString("0b9d6a3c-2222-4333-8444-955566667777"))

    @Test
    fun `deposit answers 201 with the receipt and the instant at the business offset`() {
        val command = slot<DepositCommand>()
        every { depositFunds.execute(capture(command)) } returns
            Outcome.Success(
                DepositReceipt(
                    transactionId = transactionId,
                    amount = money("100.00"),
                    balance = money("150.00"),
                    occurredAt = Instant.parse("2026-11-09T11:10:00Z"),
                ),
            )

        deposit("""{"amount":"100.00"}""")
            .andExpect {
                status { isCreated() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.transactionId") { value(transactionId.toString()) }
                jsonPath("$.amount") { value("100.00") }
                jsonPath("$.balance") { value("150.00") }
                jsonPath("$.occurredAt") { value("2026-11-09T08:10:00-03:00") }
            }
        assertThat(command.captured.accountId).isEqualTo(AccountId(accountId))
        assertThat(command.captured.amount).isEqualTo(positive("100.00"))
    }

    @Test
    fun `a whole or one-decimal amount is accepted and normalised to two decimals`() {
        every { depositFunds.execute(any()) } returns
            Outcome.Success(DepositReceipt(transactionId, money("100.50"), money("100.50"), Instant.now()))

        deposit("""{"amount":"100.5"}""").andExpect { status { isCreated() } }

        verify { depositFunds.execute(DepositCommand(AccountId(accountId), positive("100.50"))) }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"amount":"0"}""",
            """{"amount":"-5"}""",
            """{"amount":"abc"}""",
            """{"amount":"10.123"}""",
            """{}""",
            """{"amount":null}""",
        ],
    )
    fun `an invalid amount is 400 INVALID_AMOUNT and never reaches the use case`(body: String) {
        deposit(body).andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("INVALID_AMOUNT") }
            jsonPath("$.detail") { value("O valor deve ser positivo e ter no máximo 2 casas decimais.") }
        }

        verify(exactly = 0) { depositFunds.execute(any()) }
    }

    @Test
    fun `an unknown account is 401 UNAUTHORIZED`() {
        every { depositFunds.execute(any()) } returns Outcome.Failure(DepositError.AccountNotFound)

        deposit("""{"amount":"100.00"}""").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `an infrastructure failure is 503 SERVICE_UNAVAILABLE`() {
        every { depositFunds.execute(any()) } returns Outcome.Failure(DepositError.ServiceUnavailable)

        deposit("""{"amount":"100.00"}""").andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("SERVICE_UNAVAILABLE") }
        }
    }

    private fun deposit(body: String): ResultActionsDsl =
        mockMvc.post("/api/v1/me/deposits") {
            with(jwt().jwt { it.subject(accountId.toString()) })
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value

    private fun positive(value: String): PositiveMoney = (PositiveMoney.of(BigDecimal(value)) as Outcome.Success).value
}
