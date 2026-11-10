package com.kotlinbitcoins.infrastructure.support

import com.kotlinbitcoins.application.notification.NotificationEvent
import com.kotlinbitcoins.application.notification.NotificationType
import com.kotlinbitcoins.domain.shared.AccountId
import java.time.Instant
import java.util.UUID

object NotificationFixtures {
    fun depositConfirmed(
        id: UUID = UUID.randomUUID(),
        amount: String = "100.00",
        createdAt: Instant = AccountFixtures.CREATED_AT,
    ) = NotificationEvent(
        id = id,
        type = NotificationType.DEPOSIT_CONFIRMED,
        accountId = AccountId.random(),
        recipientEmail = AccountFixtures.email("ana@example.com"),
        recipientName = "Ana Souza",
        amount = AccountFixtures.money(amount),
        createdAt = createdAt,
    )
}
