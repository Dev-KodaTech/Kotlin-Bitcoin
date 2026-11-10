package com.kotlinbitcoins.api.quote

import com.kotlinbitcoins.api.support.WebMvcTestConfiguration
import com.kotlinbitcoins.application.quote.GetCurrentQuote
import com.kotlinbitcoins.application.quote.QuoteError
import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.Instant

@WebMvcTest(controllers = [QuoteController::class])
@Import(WebMvcTestConfiguration::class)
class QuoteControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var getCurrentQuote: GetCurrentQuote

    @Test
    fun `current quote answers 200 with prices as strings and quotedAt with the Sao Paulo offset`() {
        every { getCurrentQuote.execute() } returns
            Outcome.Success(
                Quote(price("399000.50"), price("400000.00"), Instant.parse("2026-11-09T11:10:00Z")),
            )

        mockMvc.get("/api/v1/quotes/current").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.buyPrice") { value("399000.50") }
            jsonPath("$.sellPrice") { value("400000.00") }
            jsonPath("$.quotedAt") { value("2026-11-09T08:10:00-03:00") }
        }
    }

    @Test
    fun `prices keep the scale received from the source`() {
        every { getCurrentQuote.execute() } returns
            Outcome.Success(
                Quote(price("399000.12345678"), price("400000"), Instant.parse("2026-11-09T11:10:00Z")),
            )

        mockMvc.get("/api/v1/quotes/current").andExpect {
            jsonPath("$.buyPrice") { value("399000.12345678") }
            jsonPath("$.sellPrice") { value("400000") }
        }
    }

    @Test
    fun `QuoteUnavailable is 503 QUOTE_UNAVAILABLE as problem details in Portuguese`() {
        every { getCurrentQuote.execute() } returns Outcome.Failure(QuoteError.QuoteUnavailable)

        mockMvc.get("/api/v1/quotes/current").andExpect {
            status { isServiceUnavailable() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("QUOTE_UNAVAILABLE") }
            jsonPath("$.detail") { value("Cotação indisponível no momento. Tente novamente em instantes.") }
        }
    }

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}
