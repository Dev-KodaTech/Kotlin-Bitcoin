package com.kotlinbitcoins.api.quote

import com.kotlinbitcoins.api.support.WebMvcTestConfiguration
import com.kotlinbitcoins.application.quote.GetQuoteHistory
import com.kotlinbitcoins.application.quote.QuoteHistoryError
import com.kotlinbitcoins.domain.quote.QuoteSnapshot
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.Instant

@WebMvcTest(controllers = [QuoteHistoryController::class])
@Import(WebMvcTestConfiguration::class)
class QuoteHistoryControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var getQuoteHistory: GetQuoteHistory

    @Test
    fun `answers 200 with the points, slotAt in the business zone and prices in the scale of the source`() {
        every { getQuoteHistory.execute() } returns
            Outcome.Success(
                listOf(
                    snapshot("2026-11-09T11:10:00Z", buy = "399000.00", sell = "400000.00"),
                    snapshot("2026-11-09T11:20:00Z", buy = "399500.5", sell = "400500.12345678"),
                ),
            )

        mockMvc
            .get("/api/v1/quotes/history") { with(jwt()) }
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.points.length()") { value(2) }
                jsonPath("$.points[0].slotAt") { value("2026-11-09T08:10:00-03:00") }
                jsonPath("$.points[0].buyPrice") { value("399000.00") }
                jsonPath("$.points[0].sellPrice") { value("400000.00") }
                jsonPath("$.points[1].slotAt") { value("2026-11-09T08:20:00-03:00") }
                jsonPath("$.points[1].buyPrice") { value("399500.5") }
                jsonPath("$.points[1].sellPrice") { value("400500.12345678") }
                jsonPath("$.points[0].length()") { value(3) }
            }
    }

    @Test
    fun `without points the list is empty`() {
        every { getQuoteHistory.execute() } returns Outcome.Success(emptyList())

        mockMvc
            .get("/api/v1/quotes/history") { with(jwt()) }
            .andExpect {
                status { isOk() }
                jsonPath("$.points.length()") { value(0) }
            }
    }

    @Test
    fun `an infrastructure failure is 503 SERVICE_UNAVAILABLE`() {
        every { getQuoteHistory.execute() } returns Outcome.Failure(QuoteHistoryError.ServiceUnavailable)

        mockMvc
            .get("/api/v1/quotes/history") { with(jwt()) }
            .andExpect {
                status { isServiceUnavailable() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("SERVICE_UNAVAILABLE") }
            }
    }

    private fun snapshot(
        slotAt: String,
        buy: String,
        sell: String,
    ): QuoteSnapshot {
        val slot = Instant.parse(slotAt)
        return QuoteSnapshot(slot, price(buy), price(sell), slot)
    }

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}
