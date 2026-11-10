package com.kotlinbitcoins.domain.ledger

/** Kinds of statement line (FR-023). */
enum class TransactionType {
    DEPOSIT,
    PURCHASE,
    REDEMPTION,
    REINVESTMENT,
}
