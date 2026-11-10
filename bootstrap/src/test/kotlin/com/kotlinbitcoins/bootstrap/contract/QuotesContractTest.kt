package com.kotlinbitcoins.bootstrap.contract

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import com.kotlinbitcoins.bootstrap.support.JwtTestTokens
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import java.time.Duration
import java.util.UUID

/**
 * US4 acceptance scenarios 1–3 over the assembled application, with WireMock standing in for the Mercado Bitcoin
 * ticker and the [com.kotlinbitcoins.bootstrap.support.MutableClock] driving the 10 s reuse and 60 s fallback
 * windows (contracts/openapi.yaml: /quotes/current).
 */
class QuotesContractTest : ApplicationTestBase() {
    private val token = "Bearer ${JwtTestTokens.valid(UUID.randomUUID(), DEFAULT_NOW)}"

    @BeforeEach
    fun sourceIsUp() = stubSource(buy = "399000.00", sell = "400000.00")

    @Test
    fun `scenario 1 - the source is up and the quote is returned with the instant it was obtained`() {
        currentQuote().andExpect {
            status { isOk() }
            jsonPath("$.buyPrice") { value("399000.00") }
            jsonPath("$.sellPrice") { value("400000.00") }
            jsonPath("$.quotedAt") { value("2026-11-09T08:10:00-03:00") }
        }
    }

    @Test
    fun `a quote younger than 10 seconds is reused without calling the source again`() {
        currentQuote().andExpect { status { isOk() } }
        stubSource(buy = "1.00", sell = "2.00")
        clock.advance(Duration.ofSeconds(9))

        currentQuote().andExpect {
            status { isOk() }
            jsonPath("$.buyPrice") { value("399000.00") }
            jsonPath("$.quotedAt") { value("2026-11-09T08:10:00-03:00") }
        }
        quoteSource.verify(1, getRequestedFor(urlEqualTo(SOURCE_PATH)))
    }

    @Test
    fun `after 10 seconds the quote is fetched again`() {
        currentQuote().andExpect { status { isOk() } }
        stubSource(buy = "410000.00", sell = "411000.00")
        clock.advance(Duration.ofSeconds(10))

        currentQuote().andExpect {
            status { isOk() }
            jsonPath("$.buyPrice") { value("410000.00") }
            jsonPath("$.sellPrice") { value("411000.00") }
            jsonPath("$.quotedAt") { value("2026-11-09T08:10:10-03:00") }
        }
    }

    @Test
    fun `scenario 2 - with the source down a quote of 30 seconds is still served with its original instant`() {
        currentQuote().andExpect { status { isOk() } }
        sourceIsDown()
        clock.advance(Duration.ofSeconds(30))

        currentQuote().andExpect {
            status { isOk() }
            jsonPath("$.buyPrice") { value("399000.00") }
            jsonPath("$.sellPrice") { value("400000.00") }
            jsonPath("$.quotedAt") { value("2026-11-09T08:10:00-03:00") }
        }
    }

    @Test
    fun `scenario 3 - with the source down and a quote older than 60 seconds the answer is 503 QUOTE_UNAVAILABLE`() {
        currentQuote().andExpect { status { isOk() } }
        sourceIsDown()
        clock.advance(Duration.ofSeconds(61))

        currentQuote().andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("QUOTE_UNAVAILABLE") }
            jsonPath("$.detail") { value("Cotação indisponível no momento. Tente novamente em instantes.") }
        }
    }

    @Test
    fun `with the source down and no previous quote the answer is 503 QUOTE_UNAVAILABLE`() {
        sourceIsDown()

        currentQuote().andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("QUOTE_UNAVAILABLE") }
        }
    }

    @Test
    fun `an invalid source answer is treated like a failure of the source`() {
        quoteSource.stubFor(
            get(urlEqualTo(SOURCE_PATH)).willReturn(aResponse().withStatus(200).withBody("""{"ticker":{}}""")),
        )

        currentQuote().andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("QUOTE_UNAVAILABLE") }
        }
    }

    @Test
    fun `the quote route requires authentication`() {
        listOf(null, "Bearer ${JwtTestTokens.MALFORMED}").forEach { authorization ->
            currentQuote(authorization).andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("UNAUTHORIZED") }
            }
        }
    }

    private fun currentQuote(authorization: String? = token): ResultActionsDsl =
        mockMvc.get("/api/v1/quotes/current") {
            authorization?.let { header(HttpHeaders.AUTHORIZATION, it) }
        }

    private fun stubSource(
        buy: String,
        sell: String,
    ) {
        quoteSource.stubFor(
            get(urlEqualTo(SOURCE_PATH)).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"ticker":{"buy":"$buy","sell":"$sell","date":1762686600}}"""),
            ),
        )
    }

    private fun sourceIsDown() {
        quoteSource.stubFor(get(urlEqualTo(SOURCE_PATH)).willReturn(aResponse().withStatus(521)))
    }

    private companion object {
        const val SOURCE_PATH = "/api/BTC/ticker/"
    }
}
