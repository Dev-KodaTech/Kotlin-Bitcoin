package com.kotlinbitcoins.application.notification

import com.kotlinbitcoins.application.port.EmailContent
import com.kotlinbitcoins.application.port.EmailMessage
import com.kotlinbitcoins.application.port.EmailSendError
import com.kotlinbitcoins.application.port.EmailSender
import com.kotlinbitcoins.domain.shared.Outcome

/**
 * Turns a [NotificationEvent] into the e-mail the client receives and hands it to the [EmailSender] (FR-030).
 * The sender's failure is returned as a value so the delivery pipeline can retry without exceptions (R-08).
 */
class SendEmailNotification(
    private val emailSender: EmailSender,
) {
    fun execute(event: NotificationEvent): Outcome<EmailSendError, Unit> =
        emailSender.send(
            EmailMessage(
                recipientEmail = event.recipientEmail,
                recipientName = event.recipientName,
                content = contentOf(event),
            ),
        )

    private fun contentOf(event: NotificationEvent): EmailContent =
        when (event.type) {
            NotificationType.DEPOSIT_CONFIRMED -> EmailContent.DepositConfirmed(event.amount)
        }
}
