package com.kotlinbitcoins.domain.investment

/** How an investment came to exist: bought by the client, or reinvested from the residue of a partial redemption. */
enum class InvestmentOrigin {
    PURCHASE,
    REINVESTMENT,
}
