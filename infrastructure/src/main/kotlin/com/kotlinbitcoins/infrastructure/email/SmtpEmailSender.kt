package com.kotlinbitcoins.infrastructure.email

import com.kotlinbitcoins.application.port.EmailMessage
import com.kotlinbitcoins.application.port.EmailSendError
import com.kotlinbitcoins.application.port.EmailSender
import com.kotlinbitcoins.domain.shared.Outcome
import jakarta.mail.MessagingException
import org.slf4j.LoggerFactory
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper

/** [EmailSender] over SMTP, used in `dev` and `test` with Mailpit. Failures come back as values. */
class SmtpEmailSender(
    private val mailSender: JavaMailSender,
    private val from: String,
) : EmailSender {
    override fun send(message: EmailMessage): Outcome<EmailSendError, Unit> =
        try {
            val rendered = EmailTemplates.render(message)
            val mime = mailSender.createMimeMessage()
            MimeMessageHelper(mime, UTF_8).apply {
                setFrom(from)
                setTo(message.recipientEmail.value)
                setSubject(rendered.subject)
                setText(rendered.body)
            }
            mailSender.send(mime)
            Outcome.Success(Unit)
        } catch (exception: MailException) {
            failure(exception)
        } catch (exception: MessagingException) {
            failure(exception)
        }

    private fun failure(exception: Exception): Outcome<EmailSendError, Unit> {
        logger.warn("SMTP send failed", exception)
        return Outcome.Failure(EmailSendError("smtp send failed: ${exception.javaClass.simpleName}"))
    }

    private companion object {
        const val UTF_8 = "UTF-8"
        val logger = LoggerFactory.getLogger(SmtpEmailSender::class.java)
    }
}
