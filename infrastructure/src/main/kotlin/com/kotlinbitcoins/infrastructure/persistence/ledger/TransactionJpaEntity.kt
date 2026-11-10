package com.kotlinbitcoins.infrastructure.persistence.ledger

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// One constructor parameter per column of `transactions`, as JPA entities mirror their table.
@Suppress("LongParameterList")
@Entity
@Table(name = "transactions")
class TransactionJpaEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,
    @Column(name = "account_id", nullable = false, updatable = false)
    var accountId: UUID,
    @Column(name = "type", nullable = false, updatable = false, length = 16)
    var type: String,
    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    var amount: BigDecimal,
    @Column(name = "btc_amount_sats", updatable = false)
    var btcAmountSats: Long?,
    @Column(name = "quote", updatable = false, precision = 19, scale = 8)
    var quote: BigDecimal?,
    @Column(name = "investment_id", updatable = false)
    var investmentId: UUID?,
    @Column(name = "request_id", updatable = false)
    var requestId: UUID?,
    @Column(name = "occurred_at", nullable = false, updatable = false)
    var occurredAt: Instant,
)
