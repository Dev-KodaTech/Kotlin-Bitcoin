package com.kotlinbitcoins.web.core.model

import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.time.ApiInstant

/** The statement (`GET /me/statement`): the period really queried and the entries, newest first (FR-026). */
data class Statement(
    val from: ApiInstant,
    val to: ApiInstant,
    val entries: List<StatementEntry>,
)

/** One line of the statement; [btcAmount] and [quote] exist only for purchases, redemptions and reinvestments. */
data class StatementEntry(
    val id: String,
    val type: EntryType,
    val amount: Money,
    val btcAmount: BtcAmount?,
    val quote: QuotePrice?,
    val investmentId: String?,
    val requestId: String?,
    val occurredAt: ApiInstant,
)

/** `StatementEntry.type` of the contract. A [Reinvestment] does not change the balance (FR-029). */
enum class EntryType {
    Deposit,
    Purchase,
    Redemption,
    Reinvestment,
}
