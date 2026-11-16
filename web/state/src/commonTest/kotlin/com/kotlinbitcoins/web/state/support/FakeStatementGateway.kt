package com.kotlinbitcoins.web.state.support

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.EntryType
import com.kotlinbitcoins.web.core.model.Statement
import com.kotlinbitcoins.web.core.model.StatementEntry
import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.statement.StatementFilter
import com.kotlinbitcoins.web.core.time.ApiInstant
import com.kotlinbitcoins.web.state.port.StatementGateway

/** Answers by filter (or with [result] when the filter has no answer of its own), recording the filters asked. */
class FakeStatementGateway : StatementGateway {
    var result: Outcome<ApiError, Statement> = Outcome.Success(statement())
    val resultByFilter = mutableMapOf<StatementFilter, Outcome<ApiError, Statement>>()
    val filters = mutableListOf<StatementFilter>()

    override suspend fun statement(filter: StatementFilter): Outcome<ApiError, Statement> {
        filters += filter
        return resultByFilter[filter] ?: result
    }
}

fun instant(raw: String): ApiInstant = ApiInstant.parseApi(raw).successValue()

fun statementEntry(
    id: String,
    type: EntryType,
    amount: String,
    requestId: String? = null,
): StatementEntry =
    StatementEntry(
        id = id,
        type = type,
        amount = money(amount),
        btcAmount = if (type == EntryType.Deposit) null else BtcAmount.parseApi("0.00100000").successValue(),
        quote = if (type == EntryType.Deposit) null else QuotePrice.parseApi("400000.00").successValue(),
        investmentId = if (type == EntryType.Deposit) null else "investment-$id",
        requestId = requestId,
        occurredAt = instant("2026-11-09T09:00:00-03:00"),
    )

/** A deposit, a purchase and one redemption of two lines plus a reinvestment, newest first. */
fun statement(
    from: String = "2026-08-11T00:00:00-03:00",
    to: String = "2026-11-09T09:00:01-03:00",
    entries: List<StatementEntry> =
        listOf(
            statementEntry("r1", EntryType.Redemption, "250.00", requestId = "req-1"),
            statementEntry("r2", EntryType.Redemption, "250.01", requestId = "req-1"),
            statementEntry("ri", EntryType.Reinvestment, "100.00", requestId = "req-1"),
            statementEntry("p", EntryType.Purchase, "400.00"),
            statementEntry("d", EntryType.Deposit, "1000.00"),
        ),
): Statement = Statement(instant(from), instant(to), entries)
