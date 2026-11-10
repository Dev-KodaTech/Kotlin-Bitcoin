package com.kotlinbitcoins.infrastructure.email

import com.kotlinbitcoins.application.port.EmailMessage
import com.kotlinbitcoins.application.port.EmailSendError
import com.kotlinbitcoins.application.port.EmailSender
import com.kotlinbitcoins.domain.shared.Outcome
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration

/**
 * [EmailSender] over the Brevo transactional API (`prod` profile). The key travels only in the `api-key` header and
 * is never logged. Any HTTP error, timeout or connection problem becomes a failure value.
 */
class BrevoEmailSender(
    baseUrl: String,
    apiKey: String,
    private val senderEmail: String,
    private val senderName: String,
    connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    readTimeout: Duration = DEFAULT_READ_TIMEOUT,
) : EmailSender {
    private val client: RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .defaultHeader(API_KEY_HEADER, apiKey)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(connectTimeout)
                    setReadTimeout(readTimeout)
                },
            ).build()

    override fun send(message: EmailMessage): Outcome<EmailSendError, Unit> =
        try {
            val rendered = EmailTemplates.render(message)
            client
                .post()
                .uri(SEND_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    BrevoRequest(
                        sender = Party(senderEmail, senderName),
                        to = listOf(Party(message.recipientEmail.value, message.recipientName)),
                        subject = rendered.subject,
                        textContent = rendered.body,
                    ),
                ).retrieve()
                .toBodilessEntity()
            Outcome.Success(Unit)
        } catch (exception: RestClientException) {
            logger.warn("Brevo send failed: {}", exception.javaClass.simpleName)
            Outcome.Failure(EmailSendError("brevo send failed: ${exception.javaClass.simpleName}"))
        }

    private data class Party(
        val email: String,
        val name: String,
    )

    private data class BrevoRequest(
        val sender: Party,
        val to: List<Party>,
        val subject: String,
        val textContent: String,
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://api.brevo.com"
        private const val SEND_PATH = "/v3/smtp/email"
        private const val API_KEY_HEADER = "api-key"
        private val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
        private val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val logger = LoggerFactory.getLogger(BrevoEmailSender::class.java)
    }
}
