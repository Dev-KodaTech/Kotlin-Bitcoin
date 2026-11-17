package com.kotlinbitcoins.web.core.statement

import com.kotlinbitcoins.web.core.model.EntryType
import com.kotlinbitcoins.web.core.model.Statement
import com.kotlinbitcoins.web.core.model.StatementEntry
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.time.ApiInstant

/** The statement as shown: the lines of one redemption request are one item (data-model §5, FR-029). */
data class GroupedStatement(
    val from: ApiInstant,
    val to: ApiInstant,
    val items: List<StatementItem>,
) {
    companion object {
        fun of(statement: Statement): GroupedStatement =
            GroupedStatement(statement.from, statement.to, StatementGrouper.group(statement.entries))
    }
}

sealed interface StatementItem {
    /** A line without `requestId`. */
    data class Single(
        val entry: StatementEntry,
    ) : StatementItem

    /**
     * Every line with the same [requestId], in the API order. [totalRedeemed] is a display sum of the
     * [EntryType.Redemption] lines only (the reinvestment does not change the balance); [occurredAt] is the instant
     * of the first line.
     */
    data class RedemptionGroup(
        val requestId: String,
        val entries: List<StatementEntry>,
        val totalRedeemed: Money,
        val occurredAt: ApiInstant,
    ) : StatementItem
}

/** Pure grouping of the statement lines; the order of the API is kept and nothing is sorted. */
object StatementGrouper {
    fun group(entries: List<StatementEntry>): List<StatementItem> {
        val byRequest = entries.filter { it.requestId != null }.groupBy { it.requestId!! }
        val emitted = mutableSetOf<String>()
        return entries.mapNotNull { entry ->
            val requestId = entry.requestId
            when {
                requestId == null -> StatementItem.Single(entry)
                emitted.add(requestId) -> redemptionGroup(requestId, byRequest.getValue(requestId))
                else -> null
            }
        }
    }

    private fun redemptionGroup(
        requestId: String,
        entries: List<StatementEntry>,
    ): StatementItem.RedemptionGroup =
        StatementItem.RedemptionGroup(
            requestId = requestId,
            entries = entries,
            totalRedeemed =
                entries
                    .filter { it.type == EntryType.Redemption }
                    .fold(Money.ZERO) { total, entry -> total + entry.amount },
            occurredAt = entries.first().occurredAt,
        )
}
