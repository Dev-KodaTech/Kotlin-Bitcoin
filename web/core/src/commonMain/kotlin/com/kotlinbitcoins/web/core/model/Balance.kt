package com.kotlinbitcoins.web.core.model

import com.kotlinbitcoins.web.core.money.Money

/** Account balance (`GET /me/balance`), shown exactly as received. */
data class Balance(
    val balance: Money,
)
