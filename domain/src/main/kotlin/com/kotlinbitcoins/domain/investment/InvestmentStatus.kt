package com.kotlinbitcoins.domain.investment

/** `CLOSED` is terminal: a closed investment is never reopened (data-model §2). */
enum class InvestmentStatus {
    ACTIVE,
    CLOSED,
}
