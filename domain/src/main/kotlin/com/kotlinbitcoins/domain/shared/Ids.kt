package com.kotlinbitcoins.domain.shared

import java.util.UUID

@JvmInline
value class AccountId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        fun random(): AccountId = AccountId(UUID.randomUUID())
    }
}

@JvmInline
value class InvestmentId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        fun random(): InvestmentId = InvestmentId(UUID.randomUUID())
    }
}

@JvmInline
value class TransactionId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        fun random(): TransactionId = TransactionId(UUID.randomUUID())
    }
}

@JvmInline
value class RedemptionRequestId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        fun random(): RedemptionRequestId = RedemptionRequestId(UUID.randomUUID())
    }
}
