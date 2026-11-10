package com.kotlinbitcoins.infrastructure.support

import com.kotlinbitcoins.application.port.EmailMessage
import com.kotlinbitcoins.application.port.EmailSendError
import com.kotlinbitcoins.application.port.EmailSender
import com.kotlinbitcoins.domain.shared.Outcome
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/** [EmailSender] for tests: records every call with its instant and fails or succeeds as told. */
class ScriptedEmailSender : EmailSender {
    @Volatile
    var isFailing = false

    val calls = CopyOnWriteArrayList<Pair<Instant, EmailMessage>>()

    override fun send(message: EmailMessage): Outcome<EmailSendError, Unit> {
        calls += Instant.now() to message
        return if (isFailing) Outcome.Failure(EmailSendError("scripted failure")) else Outcome.Success(Unit)
    }

    fun reset() {
        isFailing = false
        calls.clear()
    }
}
