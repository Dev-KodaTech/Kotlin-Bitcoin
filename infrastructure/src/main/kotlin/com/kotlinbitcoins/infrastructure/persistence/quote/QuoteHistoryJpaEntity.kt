package com.kotlinbitcoins.infrastructure.persistence.quote

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "quote_history")
class QuoteHistoryJpaEntity(
    @Id
    @Column(name = "slot_at", nullable = false, updatable = false)
    var slotAt: Instant,
    @Column(name = "buy_price", nullable = false, updatable = false, precision = 19, scale = 8)
    var buyPrice: BigDecimal,
    @Column(name = "sell_price", nullable = false, updatable = false, precision = 19, scale = 8)
    var sellPrice: BigDecimal,
    @Column(name = "captured_at", nullable = false, updatable = false)
    var capturedAt: Instant,
)
