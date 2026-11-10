package com.kotlinbitcoins.infrastructure.persistence.quote

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

interface SpringDataQuoteHistoryRepository : JpaRepository<QuoteHistoryJpaEntity, Instant> {
    /** Returns 1 when the row was inserted and 0 when the slot already had one (`ON CONFLICT DO NOTHING`). */
    @Transactional
    @Modifying
    @Query(
        value =
            "INSERT INTO quote_history (slot_at, buy_price, sell_price, captured_at) " +
                "VALUES (:slotAt, :buyPrice, :sellPrice, :capturedAt) ON CONFLICT (slot_at) DO NOTHING",
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("slotAt") slotAt: Instant,
        @Param("buyPrice") buyPrice: BigDecimal,
        @Param("sellPrice") sellPrice: BigDecimal,
        @Param("capturedAt") capturedAt: Instant,
    ): Int

    @Query("SELECT q FROM QuoteHistoryJpaEntity q WHERE q.slotAt > :since ORDER BY q.slotAt ASC")
    fun findAfter(
        @Param("since") since: Instant,
    ): List<QuoteHistoryJpaEntity>

    @Transactional
    @Modifying
    @Query("DELETE FROM QuoteHistoryJpaEntity q WHERE q.slotAt < :limit")
    fun deleteBefore(
        @Param("limit") limit: Instant,
    ): Int
}
