package com.kotlinbitcoins.infrastructure.persistence.account

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SpringDataAccountRepository : JpaRepository<AccountJpaEntity, UUID> {
    fun findByEmail(email: String): AccountJpaEntity?

    fun existsByEmail(email: String): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountJpaEntity a WHERE a.id = :id")
    fun findLockedById(
        @Param("id") id: UUID,
    ): AccountJpaEntity?
}
