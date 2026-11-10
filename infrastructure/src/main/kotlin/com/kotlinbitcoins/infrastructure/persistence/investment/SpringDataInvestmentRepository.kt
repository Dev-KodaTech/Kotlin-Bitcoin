package com.kotlinbitcoins.infrastructure.persistence.investment

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface SpringDataInvestmentRepository : JpaRepository<InvestmentJpaEntity, UUID> {
    @Query(
        "SELECT i FROM InvestmentJpaEntity i WHERE i.accountId = :accountId AND i.status = 'ACTIVE' " +
            "ORDER BY i.purchasedAt ASC, i.fifoSequence ASC",
    )
    fun findActiveInFifoOrder(
        @Param("accountId") accountId: UUID,
    ): List<InvestmentJpaEntity>

    /** Closes the investment only while it is still active; returns how many rows changed (0 or 1). */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(
        "UPDATE InvestmentJpaEntity i SET i.status = 'CLOSED', i.closedAt = :closedAt " +
            "WHERE i.id = :id AND i.status = 'ACTIVE'",
    )
    fun closeIfActive(
        @Param("id") id: UUID,
        @Param("closedAt") closedAt: Instant,
    ): Int

    @Query(value = "SELECT nextval('investments_fifo_seq')", nativeQuery = true)
    fun nextFifoSequence(): Long
}
