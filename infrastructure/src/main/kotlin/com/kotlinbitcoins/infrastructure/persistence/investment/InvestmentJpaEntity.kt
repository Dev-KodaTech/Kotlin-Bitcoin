package com.kotlinbitcoins.infrastructure.persistence.investment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// One constructor parameter per column of `investments`, as JPA entities mirror their table.
@Suppress("LongParameterList")
@Entity
@Table(name = "investments")
class InvestmentJpaEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,
    @Column(name = "account_id", nullable = false, updatable = false)
    var accountId: UUID,
    @Column(name = "purchased_at", nullable = false, updatable = false)
    var purchasedAt: Instant,
    @Column(name = "fifo_sequence", nullable = false, updatable = false)
    var fifoSequence: Long,
    @Column(name = "invested_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    var investedAmount: BigDecimal,
    @Column(name = "purchase_quote", nullable = false, updatable = false, precision = 19, scale = 8)
    var purchaseQuote: BigDecimal,
    @Column(name = "btc_amount_sats", nullable = false, updatable = false)
    var btcAmountSats: Long,
    @Column(name = "status", nullable = false, length = 16)
    var status: String,
    @Column(name = "origin", nullable = false, updatable = false, length = 16)
    var origin: String,
    @Column(name = "origin_investment_id", updatable = false)
    var originInvestmentId: UUID?,
    @Column(name = "closed_at")
    var closedAt: Instant?,
)
