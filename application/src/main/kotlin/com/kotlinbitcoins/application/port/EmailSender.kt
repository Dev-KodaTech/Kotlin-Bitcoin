package com.kotlinbitcoins.application.port

import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome

/** What the e-mail says. Each variant is rendered into Portuguese by the adapter. */
sealed interface EmailContent {
    data class DepositConfirmed(
        val amount: Money,
    ) : EmailContent
}

data class EmailMessage(
    val recipientEmail: Email,
    val recipientName: String,
    val content: EmailContent,
)

/** The provider did not accept the message. [reason] is for logs only: no secrets, no stack trace. */
data class EmailSendError(
    val reason: String,
)

fun interface EmailSender {
    fun send(message: EmailMessage): Outcome<EmailSendError, Unit>
}
