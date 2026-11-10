package com.kotlinbitcoins.api.account

import com.kotlinbitcoins.api.support.WebMvcTestConfiguration
import com.kotlinbitcoins.application.account.GetBalance
import com.kotlinbitcoins.application.account.GetBalanceError
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.util.UUID

@WebMvcTest(controllers = [BalanceController::class])
@Import(WebMvcTestConfiguration::class)
class BalanceControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var getBalance: GetBalance

    private val accountId = UUID.fromString("7f8a1c2e-1111-4222-8333-944455556666")

    @Test
    fun `balance answers 200 with the amount in reais with two decimals`() {
        every { getBalance.execute(AccountId(accountId)) } returns Outcome.Success(money("0.00"))

        balance()
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.balance") { value("0.00") }
            }
        verify { getBalance.execute(AccountId(accountId)) }
    }

    @Test
    fun `balance is formatted with two decimals`() {
        every { getBalance.execute(AccountId(accountId)) } returns Outcome.Success(money("150.50"))

        balance().andExpect { jsonPath("$.balance") { value("150.50") } }
    }

    @Test
    fun `an unknown account is 401 UNAUTHORIZED`() {
        every { getBalance.execute(AccountId(accountId)) } returns Outcome.Failure(GetBalanceError.AccountNotFound)

        balance().andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `an infrastructure failure is 503 SERVICE_UNAVAILABLE`() {
        every { getBalance.execute(AccountId(accountId)) } returns Outcome.Failure(GetBalanceError.ServiceUnavailable)

        balance().andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("SERVICE_UNAVAILABLE") }
        }
    }

    private fun balance(): ResultActionsDsl =
        mockMvc.get("/api/v1/me/balance") {
            with(jwt().jwt { it.subject(accountId.toString()) })
        }

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value
}
