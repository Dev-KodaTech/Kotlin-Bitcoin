package com.kotlinbitcoins.infrastructure.persistence.ledger

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataTransactionRepository : JpaRepository<TransactionJpaEntity, UUID>
