package com.kotlinbitcoins.api.investment

import com.kotlinbitcoins.api.support.WebMvcTestConfiguration
import com.kotlinbitcoins.application.investment.PurchaseBitcoin
import com.kotlinbitcoins.application.investment.PurchaseCommand
import com.kotlinbitcoins.application.investment.PurchaseError
import com.kotlinbitcoins.application.investment.PurchaseReceipt
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.InvestmentId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import com.kotlinbitcoins.domain.shared.QuotePrice
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

@WebMvcTest(controllers = [PurchaseController::class])
@Import(WebMvcTestConfiguration::class)
class PurchaseControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var purchaseBitcoin: PurchaseBitcoin

    private val accountId = UUID.fromString("7f8a1c2e-1111-4222-8333-944455556666")
    private val investmentId = InvestmentId(UUID.fromString("1c9d6a3c-3333-4333-8444-955566667777"))
    private val transactionId = TransactionId(UUID.fromString("0b9d6a3c-2222-4333-8444-955566667777"))

    @Test
    fun `purchase answers 201 with the receipt, the bitcoin with 8 places and the instant at the business offset`() {
        val command = slot<PurchaseCommand>()
        every { purchaseBitcoin.execute(capture(command)) } returns
            Outcome.Success(
                PurchaseReceipt(
                    investmentId = investmentId,
                    transactionId = transactionId,
                    amount = money("400.00"),
                    btcAmount = BtcAmount(100_000),
                    quote = price("400000.00"),
                    purchasedAt = Instant.parse("2026-11-09T11:10:00Z"),
                    balance = money("600.00"),
                ),
            )

        purchase("""{"amount":"400.00"}""")
            .andExpect {
                status { isCreated() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.investmentId") { value(investmentId.toString()) }
                jsonPath("$.transactionId") { value(transactionId.toString()) }
                jsonPath("$.amount") { value("400.00") }
                jsonPath("$.btcAmount") { value("0.00100000") }
                jsonPath("$.quote") { value("400000.00") }
                jsonPath("$.purchasedAt") { value("2026-11-09T08:10:00-03:00") }
                jsonPath("$.balance") { value("600.00") }
            }
        assertThat(command.captured.accountId).isEqualTo(AccountId(accountId))
        assertThat(command.captured.amount).isEqualTo(positive("400.00"))
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
        purchase(body).andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("INVALID_AMOUNT") }
        }

        verify(exactly = 0) { purchaseBitcoin.execute(any()) }
    }

    @Test
    fun `an amount worth less than one satoshi is 400 INVALID_AMOUNT`() {
        every { purchaseBitcoin.execute(any()) } returns Outcome.Failure(PurchaseError.InvalidAmount)

        purchase("""{"amount":"0.01"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_AMOUNT") }
        }
    }

    @Test
    fun `insufficient balance is 422 INSUFFICIENT_BALANCE`() {
        every { purchaseBitcoin.execute(any()) } returns Outcome.Failure(PurchaseError.InsufficientBalance)

        purchase("""{"amount":"100.01"}""").andExpect {
            status { isUnprocessableEntity() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("INSUFFICIENT_BALANCE") }
            jsonPath("$.detail") { value("Saldo insuficiente para esta compra.") }
        }
    }

    @Test
    fun `no quote is 503 QUOTE_UNAVAILABLE`() {
        every { purchaseBitcoin.execute(any()) } returns Outcome.Failure(PurchaseError.QuoteUnavailable)

        purchase("""{"amount":"10.00"}""").andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("QUOTE_UNAVAILABLE") }
        }
    }

    @Test
    fun `an unknown account is 401 UNAUTHORIZED`() {
        every { purchaseBitcoin.execute(any()) } returns Outcome.Failure(PurchaseError.AccountNotFound)

        purchase("""{"amount":"10.00"}""").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `an infrastructure failure is 503 SERVICE_UNAVAILABLE`() {
        every { purchaseBitcoin.execute(any()) } returns Outcome.Failure(PurchaseError.ServiceUnavailable)

        purchase("""{"amount":"10.00"}""").andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("SERVICE_UNAVAILABLE") }
        }
    }

    private fun purchase(body: String): ResultActionsDsl =
        mockMvc.post("/api/v1/me/purchases") {
            with(jwt().jwt { it.subject(accountId.toString()) })
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value

    private fun positive(value: String): PositiveMoney = (PositiveMoney.of(BigDecimal(value)) as Outcome.Success).value

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}
