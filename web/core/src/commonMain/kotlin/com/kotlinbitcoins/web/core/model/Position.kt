package com.kotlinbitcoins.web.core.model

import com.kotlinbitcoins.web.core.money.Money

/** Investment position (`GET /me/investments`); [investments] keep the API order (FR-019). */
data class Position(
    val quote: Quote,
    val totalCurrentValue: Money,
    val investments: List<InvestmentPosition>,
)
