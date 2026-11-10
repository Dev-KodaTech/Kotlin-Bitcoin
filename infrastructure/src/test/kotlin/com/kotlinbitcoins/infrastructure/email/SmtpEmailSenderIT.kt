package com.kotlinbitcoins.infrastructure.email

import com.fasterxml.jackson.databind.ObjectMapper
import com.kotlinbitcoins.application.port.EmailContent
import com.kotlinbitcoins.application.port.EmailMessage
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.infrastructure.support.AccountFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class SmtpEmailSenderIT {
    private val objectMapper = ObjectMapper()

    @Test
    fun `the e-mail reaches the mailbox with a Portuguese subject and body`() {
        val sender =
            SmtpEmailSender(
                mailSender(mailpit.host, mailpit.getMappedPort(SMTP_PORT)),
                "no-reply@kotlinbitcoins.example",
            )
        val message =
            EmailMessage(
                recipientEmail = AccountFixtures.email("ana.smtp@example.com"),
                recipientName = "Ana Souza",
                content = EmailContent.DepositConfirmed(AccountFixtures.money("100.00")),
            )

        val result = sender.send(message)

        assertThat(result).isEqualTo(Outcome.Success(Unit))
        val summary = objectMapper.readTree(get("/api/v1/search?query=to:ana.smtp@example.com"))["messages"][0]
        assertThat(summary["Subject"].asText()).contains("Depósito confirmado").contains("R$ 100,00")
        val full = objectMapper.readTree(get("/api/v1/message/${summary["ID"].asText()}"))
        assertThat(full["Text"].asText()).contains("Ana Souza").contains("R$ 100,00")
        assertThat(full["From"]["Address"].asText()).isEqualTo("no-reply@kotlinbitcoins.example")
    }

    @Test
    fun `an unreachable SMTP server is a failure value, not an exception`() {
        val sender = SmtpEmailSender(mailSender("localhost", 1), "no-reply@kotlinbitcoins.example")

        val result =
            sender.send(
                EmailMessage(
                    AccountFixtures.email("ana@example.com"),
                    "Ana",
                    EmailContent.DepositConfirmed(AccountFixtures.money("1.00")),
                ),
            )

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
    }

    private fun mailSender(
        host: String,
        port: Int,
    ) = JavaMailSenderImpl().apply {
        this.host = host
        this.port = port
        javaMailProperties["mail.smtp.connectiontimeout"] = "2000"
        javaMailProperties["mail.smtp.timeout"] = "2000"
        javaMailProperties["mail.smtp.writetimeout"] = "2000"
    }

    private fun get(path: String): String {
        val uri = URI.create("http://${mailpit.host}:${mailpit.getMappedPort(API_PORT)}$path")
        return HttpClient
            .newHttpClient()
            .send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString())
            .body()
    }

    private companion object {
        const val SMTP_PORT = 1025
        const val API_PORT = 8025
        val mailpit: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("axllent/mailpit:v1.27")).withExposedPorts(SMTP_PORT, API_PORT)

        @JvmStatic
        @BeforeAll
        fun start() = mailpit.start()

        @JvmStatic
        @AfterAll
        fun stop() = mailpit.stop()
    }
}
