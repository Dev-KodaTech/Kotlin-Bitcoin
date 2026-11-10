package com.kotlinbitcoins.infrastructure.persistence.account

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "accounts")
class AccountJpaEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,
    @Column(name = "name", nullable = false, length = 200)
    var name: String,
    @Column(name = "email", nullable = false, length = 320)
    var email: String,
    @Column(name = "password_hash", nullable = false, length = 100)
    var passwordHash: String,
    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    var balance: BigDecimal,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
)
