package com.kotlinbitcoins.infrastructure.messaging

import com.kotlinbitcoins.application.notification.NotificationEvent
import com.kotlinbitcoins.application.notification.NotificationType
import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * JSON form of a [NotificationEvent], stored in `outbox_events.payload` and used as the message body. It holds the
 * recipient and the operation data only: never a password, hash or token (FR-007).
 */
data class NotificationPayload(
    val id: String,
    val type: String,
    val accountId: String,
    val recipientEmail: String,
    val recipientName: String,
    val amount: String,
    val createdAt: String,
) {
    /** Returns null when the payload does not describe a valid event (corrupt or foreign message). */
    @Suppress("ReturnCount", "SwallowedException")
    fun toEvent(): NotificationEvent? {
        val email = Email.of(recipientEmail) as? Outcome.Success ?: return null
        val money = runCatching { Money.of(BigDecimal(amount)) }.getOrNull() as? Outcome.Success ?: return null
        return runCatching {
            NotificationEvent(
                id = UUID.fromString(id),
                type = NotificationType.valueOf(type),
                accountId = AccountId(UUID.fromString(accountId)),
                recipientEmail = email.value,
                recipientName = recipientName,
                amount = money.value,
                createdAt = Instant.parse(createdAt),
            )
        }.getOrNull()
    }

    companion object {
        fun of(event: NotificationEvent) =
            NotificationPayload(
                id = event.id.toString(),
                type = event.type.name,
                accountId = event.accountId.toString(),
                recipientEmail = event.recipientEmail.value,
                recipientName = event.recipientName,
                amount = event.amount.amount.toPlainString(),
                createdAt = event.createdAt.toString(),
            )
    }
}
