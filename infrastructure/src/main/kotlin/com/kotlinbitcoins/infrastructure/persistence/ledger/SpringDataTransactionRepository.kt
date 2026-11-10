package com.kotlinbitcoins.infrastructure.persistence.ledger

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SpringDataTransactionRepository : JpaRepository<TransactionJpaEntity, UUID> {
    @Query(
        "SELECT t FROM TransactionJpaEntity t WHERE t.accountId = :accountId " +
            "AND t.occurredAt >= :from AND t.occurredAt < :to ORDER BY t.occurredAt DESC, t.id DESC",
    )
    fun findBetween(
        @Param("accountId") accountId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): List<TransactionJpaEntity>
}
