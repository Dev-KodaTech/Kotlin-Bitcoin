package com.kotlinbitcoins.api.investment

import com.kotlinbitcoins.api.support.WebMvcTestConfiguration
import com.kotlinbitcoins.application.investment.LiquidationReceipt
import com.kotlinbitcoins.application.investment.RedeemCommand
import com.kotlinbitcoins.application.investment.RedeemInvestments
import com.kotlinbitcoins.application.investment.RedemptionError
import com.kotlinbitcoins.application.investment.RedemptionReceipt
import com.kotlinbitcoins.application.investment.ReinvestmentReceipt
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.InvestmentId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import com.kotlinbitcoins.domain.shared.QuotePrice
import com.kotlinbitcoins.domain.shared.RedemptionRequestId
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

@WebMvcTest(controllers = [RedemptionController::class])
@Import(WebMvcTestConfiguration::class)
class RedemptionControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var redeemInvestments: RedeemInvestments

    private val accountId = UUID.fromString("7f8a1c2e-1111-4222-8333-944455556666")
    private val requestId = RedemptionRequestId(UUID.fromString("5a5a1c2e-1111-4222-8333-944455556666"))
    private val investmentA = InvestmentId(UUID.fromString("1c9d6a3c-3333-4333-8444-955566667777"))
    private val investmentB = InvestmentId(UUID.fromString("2c9d6a3c-3333-4333-8444-955566667777"))
    private val reinvestmentId = InvestmentId(UUID.fromString("3c9d6a3c-3333-4333-8444-955566667777"))

    @Test
    fun `redemption answers 201 with liquidations, reinvestment, bitcoin with 8 places and local instants`() {
        val command = slot<RedeemCommand>()
        every { redeemInvestments.execute(capture(command)) } returns Outcome.Success(receipt())

        redeem("""{"amount":"250.00"}""")
            .andExpect {
                status { isCreated() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.requestId") { value(requestId.toString()) }
                jsonPath("$.amount") { value("250.00") }
                jsonPath("$.btcSold") { value("0.00125000") }
                jsonPath("$.quote") { value("200000.00") }
                jsonPath("$.balance") { value("260.00") }
                jsonPath("$.occurredAt") { value("2026-11-09T08:10:00-03:00") }
                jsonPath("$.liquidations.length()") { value(2) }
                jsonPath("$.liquidations[0].investmentId") { value(investmentA.toString()) }
                jsonPath("$.liquidations[0].amount") { value("200.00") }
                jsonPath("$.liquidations[0].btcAmount") { value("0.00100000") }
                jsonPath("$.liquidations[0].isPartial") { value(false) }
                jsonPath("$.liquidations[1].investmentId") { value(investmentB.toString()) }
                jsonPath("$.liquidations[1].amount") { value("50.00") }
                jsonPath("$.liquidations[1].btcAmount") { value("0.00025000") }
                jsonPath("$.liquidations[1].isPartial") { value(true) }
                jsonPath("$.reinvestment.investmentId") { value(reinvestmentId.toString()) }
                jsonPath("$.reinvestment.btcAmount") { value("0.00025000") }
                jsonPath("$.reinvestment.investedAmount") { value("100.00") }
                jsonPath("$.reinvestment.purchaseQuote") { value("400000.00") }
                jsonPath("$.reinvestment.purchasedAt") { value("2026-11-02T07:00:00-03:00") }
            }
        assertThat(command.captured.accountId).isEqualTo(AccountId(accountId))
        assertThat(command.captured.amount).isEqualTo(positive("250.00"))
    }

    @Test
    fun `a redemption that leaves no residue has a null reinvestment`() {
        every { redeemInvestments.execute(any()) } returns Outcome.Success(receipt().copy(reinvestment = null))

        redeem("""{"amount":"300.00"}""").andExpect {
            status { isCreated() }
            jsonPath("$.reinvestment") { value(null) }
        }
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
        redeem(body).andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("INVALID_AMOUNT") }
        }

        verify(exactly = 0) { redeemInvestments.execute(any()) }
    }

    @Test
    fun `an amount above the current value is 422 REDEMPTION_EXCEEDS_AVAILABLE`() {
        every { redeemInvestments.execute(any()) } returns Outcome.Failure(RedemptionError.RedemptionExceedsAvailable)

        redeem("""{"amount":"800.01"}""").andExpect {
            status { isUnprocessableEntity() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("REDEMPTION_EXCEEDS_AVAILABLE") }
            jsonPath("$.detail") { value("O valor solicitado excede o valor atual dos seus investimentos.") }
        }
    }

    @Test
    fun `no quote is 503 QUOTE_UNAVAILABLE`() {
        every { redeemInvestments.execute(any()) } returns Outcome.Failure(RedemptionError.QuoteUnavailable)

        redeem("""{"amount":"10.00"}""").andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("QUOTE_UNAVAILABLE") }
        }
    }

    @Test
    fun `an unknown account is 401 UNAUTHORIZED`() {
        every { redeemInvestments.execute(any()) } returns Outcome.Failure(RedemptionError.AccountNotFound)

        redeem("""{"amount":"10.00"}""").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `an infrastructure failure is 503 SERVICE_UNAVAILABLE`() {
        every { redeemInvestments.execute(any()) } returns Outcome.Failure(RedemptionError.ServiceUnavailable)

        redeem("""{"amount":"10.00"}""").andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("SERVICE_UNAVAILABLE") }
        }
    }

    private fun receipt() =
        RedemptionReceipt(
            requestId = requestId,
            amount = money("250.00"),
            btcSold = BtcAmount(125_000),
            quote = price("200000.00"),
            balance = money("260.00"),
            occurredAt = Instant.parse("2026-11-09T11:10:00Z"),
            liquidations =
                listOf(
                    LiquidationReceipt(investmentA, money("200.00"), BtcAmount(100_000), isPartial = false),
                    LiquidationReceipt(investmentB, money("50.00"), BtcAmount(25_000), isPartial = true),
                ),
            reinvestment =
                ReinvestmentReceipt(
                    investmentId = reinvestmentId,
                    btcAmount = BtcAmount(25_000),
                    investedAmount = money("100.00"),
                    purchaseQuote = price("400000.00"),
                    purchasedAt = Instant.parse("2026-11-02T10:00:00Z"),
                ),
        )

    private fun redeem(body: String): ResultActionsDsl =
        mockMvc.post("/api/v1/me/redemptions") {
            with(jwt().jwt { it.subject(accountId.toString()) })
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value

    private fun positive(value: String): PositiveMoney = (PositiveMoney.of(BigDecimal(value)) as Outcome.Success).value

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}
