@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.kotlinbitcoins.api.ledger

import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.application.ledger.Statement
import com.kotlinbitcoins.domain.ledger.Transaction

/** A line of the statement; `btcAmount`, `quote`, `investmentId` and `requestId` are `null` when not applicable. */
data class StatementEntryDto(
    val id: String,
    val type: String,
    val amount: String,
    val btcAmount: String?,
    val quote: String?,
    val investmentId: String?,
    val requestId: String?,
    val occurredAt: String,
) {
    companion object {
        fun of(
            transaction: Transaction,
            formats: ApiFormats,
        ) = StatementEntryDto(
            id = transaction.id.toString(),
            type = transaction.type.name,
            amount = formats.money(transaction.amount),
            btcAmount = transaction.btcAmount?.let(formats::btc),
            quote = transaction.quote?.let(formats::quote),
            investmentId = transaction.investmentId?.toString(),
            requestId = transaction.requestId?.toString(),
            occurredAt = formats.instant(transaction.occurredAt),
        )
    }
}

/** `from` is inclusive and `to` exclusive: the period that was actually queried. */
data class StatementResponse(
    val from: String,
    val to: String,
    val entries: List<StatementEntryDto>,
) {
    companion object {
        fun of(
            statement: Statement,
            formats: ApiFormats,
        ) = StatementResponse(
            from = formats.instant(statement.from),
            to = formats.instant(statement.to),
            entries = statement.entries.map { StatementEntryDto.of(it, formats) },
        )
    }
}
