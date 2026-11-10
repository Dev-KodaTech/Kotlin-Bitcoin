package com.kotlinbitcoins.infrastructure.messaging

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface SpringDataOutboxEventRepository : JpaRepository<OutboxEventJpaEntity, UUID> {
    /**
     * Locks up to [limit] unpublished events for the current transaction. `SKIP LOCKED` lets several replicas
     * run the relay at once, each taking a different set, without publishing an event twice (R-08).
     */
    @Query(
        value =
            "SELECT * FROM outbox_events WHERE published_at IS NULL " +
                "ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED",
        nativeQuery = true,
    )
    fun lockUnpublished(
        @Param("limit") limit: Int,
    ): List<OutboxEventJpaEntity>

    @Transactional
    @Modifying
    @Query("UPDATE OutboxEventJpaEntity e SET e.publishedAt = :at WHERE e.id = :id")
    fun markPublished(
        @Param("id") id: UUID,
        @Param("at") at: Instant,
    ): Int

    /** Returns 1 only for the call that sets the mark; later calls for the same event return 0. */
    @Transactional
    @Modifying
    @Query("UPDATE OutboxEventJpaEntity e SET e.sentAt = :at WHERE e.id = :id AND e.sentAt IS NULL")
    fun markSentIfNotSent(
        @Param("id") id: UUID,
        @Param("at") at: Instant,
    ): Int

    fun existsByIdAndSentAtIsNotNull(id: UUID): Boolean
}
