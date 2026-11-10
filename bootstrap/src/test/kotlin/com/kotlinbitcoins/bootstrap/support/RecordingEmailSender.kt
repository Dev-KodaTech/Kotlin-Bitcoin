package com.kotlinbitcoins.bootstrap.support

import com.kotlinbitcoins.application.port.EmailMessage
import com.kotlinbitcoins.application.port.EmailSendError
import com.kotlinbitcoins.application.port.EmailSender
import com.kotlinbitcoins.domain.shared.Outcome
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [EmailSender] for contract tests, registered as `@Primary` in place of the real adapter. It records the messages
 * it accepts and counts every attempt; while [isFailing] is true it rejects them, like a provider that is down.
 */
class RecordingEmailSender : EmailSender {
    @Volatile
    var isFailing = false

    private val accepted = CopyOnWriteArrayList<EmailMessage>()

    @Volatile
    var attempts = 0
        private set

    val messages: List<EmailMessage> get() = accepted.toList()

    override fun send(message: EmailMessage): Outcome<EmailSendError, Unit> {
        attempts++
        if (isFailing) return Outcome.Failure(EmailSendError("recording sender is failing"))
        accepted += message
        return Outcome.Success(Unit)
    }

    fun reset() {
        isFailing = false
        attempts = 0
        accepted.clear()
    }
}
