package com.kotlinbitcoins.infrastructure.messaging

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/** Row of the Transactional Outbox (R-08). [payload] is the notification as JSON text stored in a JSONB column. */
@Entity
@Table(name = "outbox_events")
class OutboxEventJpaEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,
    @Column(name = "type", nullable = false, updatable = false, length = 32)
    var type: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false)
    var payload: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
    @Column(name = "published_at")
    var publishedAt: Instant? = null,
    @Column(name = "sent_at")
    var sentAt: Instant? = null,
)
