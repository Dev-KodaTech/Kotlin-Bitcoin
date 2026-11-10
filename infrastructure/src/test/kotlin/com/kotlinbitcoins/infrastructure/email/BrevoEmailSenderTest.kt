package com.kotlinbitcoins.infrastructure.email

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.kotlinbitcoins.application.port.EmailContent
import com.kotlinbitcoins.application.port.EmailMessage
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.infrastructure.support.AccountFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class BrevoEmailSenderTest {
    private val sender =
        BrevoEmailSender(
            baseUrl = server.baseUrl(),
            apiKey = API_KEY,
            senderEmail = "no-reply@kotlinbitcoins.example",
            senderName = "KotlinBitcoins",
            connectTimeout = Duration.ofSeconds(1),
            readTimeout = Duration.ofMillis(500),
        )

    private val message =
        EmailMessage(
            recipientEmail = AccountFixtures.email("ana@example.com"),
            recipientName = "Ana Souza",
            content = EmailContent.DepositConfirmed(AccountFixtures.money("100.00")),
        )

    @BeforeEach
    fun reset() = server.resetAll()

    @Test
    fun `posts the message to the transactional e-mail endpoint with the api key`() {
        server.stubFor(
            post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(201).withBody("""{"messageId":"<1@x>"}""")),
        )

        val result = sender.send(message)

        assertThat(result).isEqualTo(Outcome.Success(Unit))
        server.verify(
            postRequestedFor(urlEqualTo(PATH))
                .withHeader("api-key", equalTo(API_KEY))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(matchingJsonPath("$.to[0].email", equalTo("ana@example.com")))
                .withRequestBody(matchingJsonPath("$.to[0].name", equalTo("Ana Souza")))
                .withRequestBody(matchingJsonPath("$.sender.email", equalTo("no-reply@kotlinbitcoins.example")))
                .withRequestBody(matchingJsonPath("$.subject", containing("R$ 100,00")))
                .withRequestBody(matchingJsonPath("$.textContent", containing("R$ 100,00"))),
        )
    }

    @Test
    fun `client and server errors are failure values`() {
        listOf(400, 401, 429, 500, 503).forEach { status ->
            server.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(status)))

            val result = sender.send(message)

            assertThat(result).describedAs("status $status").isInstanceOf(Outcome.Failure::class.java)
        }
    }

    @Test
    fun `a response slower than the read timeout is a failure value`() {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(201).withFixedDelay(2_000)))

        assertThat(sender.send(message)).isInstanceOf(Outcome.Failure::class.java)
    }

    @Test
    fun `an unreachable provider is a failure value and the reason never contains the api key`() {
        val unreachable =
            BrevoEmailSender(
                "http://localhost:1",
                API_KEY,
                "a@b.co",
                "X",
                Duration.ofMillis(300),
                Duration.ofMillis(300),
            )

        val result = unreachable.send(message)

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error.reason).doesNotContain(API_KEY)
    }

    private companion object {
        const val PATH = "/v3/smtp/email"
        const val API_KEY = "test-api-key"
        val server = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @BeforeAll
        fun start() = server.start()

        @JvmStatic
        @AfterAll
        fun stop() = server.stop()
    }
}
