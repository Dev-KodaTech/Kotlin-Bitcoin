package com.kotlinbitcoins.application.notification

import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Money
import java.time.Instant
import java.util.UUID

enum class NotificationType {
    DEPOSIT_CONFIRMED,
    PURCHASE_CONFIRMED,
}

/**
 * Fact that must be told to the client by e-mail (FR-030). It carries the recipient as it was when the operation
 * happened and never any secret. [id] is the deduplication key of the delivery pipeline (R-08). [btcAmount] is the
 * bitcoin bought and is present exactly in a [NotificationType.PURCHASE_CONFIRMED].
 */
data class NotificationEvent(
    val id: UUID,
    val type: NotificationType,
    val accountId: AccountId,
    val recipientEmail: Email,
    val recipientName: String,
    val amount: Money,
    val createdAt: Instant,
    val btcAmount: BtcAmount? = null,
) {
    init {
        require((type == NotificationType.PURCHASE_CONFIRMED) == (btcAmount != null)) {
            "btcAmount is present exactly in a PURCHASE_CONFIRMED event"
        }
    }
}
