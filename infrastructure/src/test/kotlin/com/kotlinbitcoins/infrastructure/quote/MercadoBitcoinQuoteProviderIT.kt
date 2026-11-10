package com.kotlinbitcoins.infrastructure.quote

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.http.Fault
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.QuoteProviderError
import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.Outcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class MercadoBitcoinQuoteProviderIT {
    private val receivedAt = Instant.parse("2026-11-09T11:10:00Z")
    private val clock = Clock { receivedAt }

    private val provider =
        MercadoBitcoinQuoteProvider(
            QuoteProviderProperties(
                url = "${server.baseUrl()}$PATH",
                connectTimeout = Duration.ofSeconds(1),
                readTimeout = Duration.ofMillis(500),
            ),
            clock,
        )

    @BeforeEach
    fun reset() = server.resetAll()

    @Test
    fun `reads sell and buy prices as decimals keeping the original scale`() {
        stubTicker(buy = "399000.12345678", sell = "400000.00")

        val result = provider.fetchCurrent()

        val quote = (result as Outcome.Success<Quote>).value
        assertThat(quote.sellPrice.value).isEqualByComparingTo("400000.00").hasScaleOf(2)
        assertThat(quote.buyPrice.value).isEqualByComparingTo("399000.12345678").hasScaleOf(8)
        server.verify(getRequestedFor(urlEqualTo(PATH)))
    }

    @Test
    fun `prices that cannot be represented by a double survive exactly`() {
        stubTicker(buy = "0.10000001", sell = "123456789.12345679")

        val quote = (provider.fetchCurrent() as Outcome.Success<Quote>).value

        assertThat(quote.buyPrice.value.toPlainString()).isEqualTo("0.10000001")
        assertThat(quote.sellPrice.value.toPlainString()).isEqualTo("123456789.12345679")
    }

    @Test
    fun `prices sent as JSON numbers are also read without going through double`() {
        server.stubFor(
            get(urlEqualTo(PATH)).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"ticker":{"buy":123456789.12345679,"sell":400000.50,"date":1762686600}}"""),
            ),
        )

        val quote = (provider.fetchCurrent() as Outcome.Success<Quote>).value

        assertThat(quote.buyPrice.value.toPlainString()).isEqualTo("123456789.12345679")
        assertThat(quote.sellPrice.value.toPlainString()).isEqualTo("400000.50")
    }

    @Test
    fun `quotedAt is the instant the answer was received, ignoring ticker date`() {
        stubTicker(buy = "399000.00", sell = "400000.00", date = "1000000000")

        val quote = (provider.fetchCurrent() as Outcome.Success<Quote>).value

        assertThat(quote.quotedAt).isEqualTo(receivedAt)
    }

    @Test
    fun `HTTP 521 and 500 are failure values`() {
        listOf(521, 500, 503, 404).forEach { status ->
            server.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(status)))

            assertFailure("status $status")
        }
    }

    @Test
    fun `a read timeout is a failure value`() {
        server.stubFor(
            get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody(TICKER).withFixedDelay(2_000)),
        )

        assertFailure("read timeout")
    }

    @Test
    fun `a connection reset is a failure value`() {
        server.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

        assertFailure("connection reset")
    }

    @Test
    fun `an unreachable source is a failure value`() {
        val unreachable =
            MercadoBitcoinQuoteProvider(
                QuoteProviderProperties(
                    "http://localhost:1/api/BTC/ticker/",
                    Duration.ofMillis(300),
                    Duration.ofMillis(300),
                ),
                clock,
            )

        assertThat(unreachable.fetchCurrent()).isInstanceOf(Outcome.Failure::class.java)
    }

    @Test
    fun `invalid JSON and unexpected shapes are failure values`() {
        listOf(
            "not json",
            "",
            "{}",
            """{"ticker":{}}""",
            """{"ticker":{"buy":"399000.00"}}""",
            """{"ticker":{"sell":"400000.00"}}""",
            """{"ticker":{"buy":"abc","sell":"400000.00"}}""",
            """{"ticker":{"buy":null,"sell":"400000.00"}}""",
            """{"ticker":{"buy":"399000.00","sell":{"x":1}}}""",
        ).forEach { body ->
            server.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody(body)))

            assertFailure("body '$body'")
        }
    }

    @Test
    fun `zero, negative and over-precise prices are failure values`() {
        listOf(
            "0" to "400000.00",
            "399000.00" to "0.00",
            "-1.00" to "400000.00",
            "399000.00" to "400000.123456789",
        ).forEach { (buy, sell) ->
            stubTicker(buy = buy, sell = sell)

            assertFailure("buy=$buy sell=$sell")
        }
    }

    @Test
    fun `the failure reason does not leak the response body`() {
        server.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody("secret-body")))

        val error = (provider.fetchCurrent() as Outcome.Failure<QuoteProviderError>).error

        assertThat(error.reason).doesNotContain("secret-body")
    }

    @Test
    fun `defaults follow the research decision - 1 second to connect and 2 seconds to read`() {
        val properties = QuoteProviderProperties(url = "http://x/")

        assertThat(properties.connectTimeout).isEqualTo(Duration.ofSeconds(1))
        assertThat(properties.readTimeout).isEqualTo(Duration.ofSeconds(2))
    }

    private fun assertFailure(description: String) {
        assertThat(provider.fetchCurrent()).describedAs(description).isInstanceOf(Outcome.Failure::class.java)
    }

    private fun stubTicker(
        buy: String,
        sell: String,
        date: String = "1762686600",
    ) {
        server.stubFor(
            get(urlEqualTo(PATH)).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"ticker":{"buy":"$buy","sell":"$sell","date":$date}}"""),
            ),
        )
    }

    private companion object {
        const val PATH = "/api/BTC/ticker/"
        const val TICKER = """{"ticker":{"buy":"399000.00","sell":"400000.00","date":1762686600}}"""
        val server = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @BeforeAll
        fun start() = server.start()

        @JvmStatic
        @AfterAll
        fun stop() = server.stop()
    }
}
