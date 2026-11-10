package com.kotlinbitcoins.infrastructure.persistence.investment

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SpringDataInvestmentRepository : JpaRepository<InvestmentJpaEntity, UUID> {
    @Query(
        "SELECT i FROM InvestmentJpaEntity i WHERE i.accountId = :accountId AND i.status = 'ACTIVE' " +
            "ORDER BY i.purchasedAt ASC, i.fifoSequence ASC",
    )
    fun findActiveInFifoOrder(
        @Param("accountId") accountId: UUID,
    ): List<InvestmentJpaEntity>

    @Query(value = "SELECT nextval('investments_fifo_seq')", nativeQuery = true)
    fun nextFifoSequence(): Long
}
