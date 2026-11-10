package com.kotlinbitcoins.api.investment

import com.kotlinbitcoins.api.support.WebMvcTestConfiguration
import com.kotlinbitcoins.application.investment.GetInvestmentPosition
import com.kotlinbitcoins.application.investment.GetInvestmentPositionError
import com.kotlinbitcoins.application.investment.InvestmentPosition
import com.kotlinbitcoins.application.investment.PositionItem
import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.InvestmentId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import com.ninjasquad.springmockk.MockkBean
import io.mockk.confirmVerified
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
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [PositionController::class])
@Import(WebMvcTestConfiguration::class)
class PositionControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var getInvestmentPosition: GetInvestmentPosition

    private val accountId = UUID.fromString("7f8a1c2e-1111-4222-8333-944455556666")
    private val firstId = InvestmentId(UUID.fromString("1c9d6a3c-3333-4333-8444-955566667777"))
    private val secondId = InvestmentId(UUID.fromString("2c9d6a3c-3333-4333-8444-955566667777"))
    private val quote = Quote(price("440000.00"), price("441000.00"), Instant.parse("2026-11-09T11:10:00Z"))

    @Test
    fun `answers 200 with the quote, the total and the investments in FIFO order`() {
        every { getInvestmentPosition.execute(AccountId(accountId)) } returns
            Outcome.Success(
                InvestmentPosition(
                    quote = quote,
                    totalCurrentValue = money("660.00"),
                    items =
                        listOf(
                            item(firstId, "2026-11-01T12:00:00Z", "400.00", "400000.00", 100_000, "10.00", "440.00"),
                            item(secondId, "2026-11-02T12:00:00Z", "200.00", "440000.00", 50_000, "-5.50", "220.00"),
                        ),
                ),
            )

        position()
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.quote.buyPrice") { value("440000.00") }
                jsonPath("$.quote.sellPrice") { value("441000.00") }
                jsonPath("$.quote.quotedAt") { value("2026-11-09T08:10:00-03:00") }
                jsonPath("$.totalCurrentValue") { value("660.00") }
                jsonPath("$.investments.length()") { value(2) }
                jsonPath("$.investments[0].id") { value(firstId.toString()) }
                jsonPath("$.investments[0].purchasedAt") { value("2026-11-01T09:00:00-03:00") }
                jsonPath("$.investments[0].investedAmount") { value("400.00") }
                jsonPath("$.investments[0].purchaseQuote") { value("400000.00") }
                jsonPath("$.investments[0].btcAmount") { value("0.00100000") }
                jsonPath("$.investments[0].variationPercent") { value("10.00") }
                jsonPath("$.investments[0].currentValue") { value("440.00") }
                jsonPath("$.investments[1].id") { value(secondId.toString()) }
                jsonPath("$.investments[1].variationPercent") { value("-5.50") }
            }
    }

    @Test
    fun `without investments the list is empty`() {
        every { getInvestmentPosition.execute(AccountId(accountId)) } returns
            Outcome.Success(InvestmentPosition(quote, Money.ZERO, emptyList()))

        position().andExpect {
            status { isOk() }
            jsonPath("$.totalCurrentValue") { value("0.00") }
            jsonPath("$.investments.length()") { value(0) }
        }
    }

    @Test
    fun `no quote is 503 QUOTE_UNAVAILABLE`() {
        every { getInvestmentPosition.execute(AccountId(accountId)) } returns
            Outcome.Failure(GetInvestmentPositionError.QuoteUnavailable)

        position().andExpect {
            status { isServiceUnavailable() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("QUOTE_UNAVAILABLE") }
        }
    }

    @Test
    fun `an infrastructure failure is 503 SERVICE_UNAVAILABLE`() {
        every { getInvestmentPosition.execute(AccountId(accountId)) } returns
            Outcome.Failure(GetInvestmentPositionError.ServiceUnavailable)

        position().andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("SERVICE_UNAVAILABLE") }
        }
    }

    @Test
    fun `without a token it is 401 and never reaches the use case`() {
        mockMvc.get("/api/v1/me/investments").andExpect { status { isUnauthorized() } }

        verify(exactly = 0) { getInvestmentPosition.execute(AccountId(accountId)) }
        confirmVerified(getInvestmentPosition)
    }

    private fun position(): ResultActionsDsl =
        mockMvc.get("/api/v1/me/investments") {
            with(jwt().jwt { it.subject(accountId.toString()) })
        }

    @Suppress("LongParameterList")
    private fun item(
        id: InvestmentId,
        purchasedAt: String,
        invested: String,
        purchaseQuote: String,
        sats: Long,
        variation: String,
        currentValue: String,
    ) = PositionItem(
        investmentId = id,
        purchasedAt = Instant.parse(purchasedAt),
        investedAmount = money(invested),
        purchaseQuote = price(purchaseQuote),
        btcAmount = BtcAmount(sats),
        variationPercent = BigDecimal(variation),
        currentValue = money(currentValue),
    )

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}
