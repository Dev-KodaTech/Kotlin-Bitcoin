package com.kotlinbitcoins.web.core.statement

import com.kotlinbitcoins.web.core.model.EntryType
import com.kotlinbitcoins.web.core.model.StatementEntry
import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.support.successValue
import com.kotlinbitcoins.web.core.time.ApiInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StatementGrouperTest {
    private fun entry(
        id: String,
        type: EntryType,
        amount: String,
        requestId: String? = null,
        at: String = "2026-11-09T09:00:00-03:00",
    ): StatementEntry =
        StatementEntry(
            id = id,
            type = type,
            amount = Money.parseApi(amount).successValue(),
            btcAmount = if (type == EntryType.Deposit) null else BtcAmount.parseApi("0.00100000").successValue(),
            quote = if (type == EntryType.Deposit) null else QuotePrice.parseApi("400000.00").successValue(),
            investmentId = if (type == EntryType.Deposit) null else "investment-$id",
            requestId = requestId,
            occurredAt = ApiInstant.parseApi(at).successValue(),
        )

    @Test
    fun `entries without requestId are singles in the order of the API`() {
        val entries =
            listOf(
                entry("c", EntryType.Purchase, "400.00"),
                entry("a", EntryType.Deposit, "1000.00"),
                entry("b", EntryType.Purchase, "100.00"),
            )

        val items = StatementGrouper.group(entries)

        assertEquals(entries.map { StatementItem.Single(it) }, items)
    }

    @Test
    fun `entries with the same requestId become one group at the position of the first one`() {
        val first = entry("r1", EntryType.Redemption, "250.00", requestId = "req-1", at = "2026-11-09T09:00:00-03:00")
        val second = entry("r2", EntryType.Redemption, "250.01", requestId = "req-1", at = "2026-11-09T09:00:00-03:00")
        val reinvestment = entry("ri", EntryType.Reinvestment, "100.00", requestId = "req-1")
        val newer = entry("p2", EntryType.Purchase, "50.00", at = "2026-11-09T10:00:00-03:00")
        val older = entry("d", EntryType.Deposit, "1000.00", at = "2026-11-09T08:00:00-03:00")
        // An entry of the group after an unrelated one: still in the group, in the API order.
        val entries = listOf(newer, first, second, older, reinvestment)

        val items = StatementGrouper.group(entries)

        assertEquals(3, items.size)
        assertEquals(StatementItem.Single(newer), items[0])
        val group = assertIs<StatementItem.RedemptionGroup>(items[1])
        assertEquals("req-1", group.requestId)
        assertEquals(listOf(first, second, reinvestment), group.entries)
        assertEquals(first.occurredAt, group.occurredAt)
        assertEquals(StatementItem.Single(older), items[2])
    }

    @Test
    fun `the total redeemed is the exact sum of the redemption lines only`() {
        val entries =
            listOf(
                entry("r1", EntryType.Redemption, "250.00", requestId = "req-1"),
                entry("r2", EntryType.Redemption, "250.01", requestId = "req-1"),
                entry("ri", EntryType.Reinvestment, "100.00", requestId = "req-1"),
            )

        val group = assertIs<StatementItem.RedemptionGroup>(StatementGrouper.group(entries).single())

        assertEquals("500.01", group.totalRedeemed.toApiString())
        assertEquals("R$ 500,01", group.totalRedeemed.format())
    }

    @Test
    fun `a group cut by the filter is still a group`() {
        val only = entry("r2", EntryType.Redemption, "250.01", requestId = "req-1")

        val group = assertIs<StatementItem.RedemptionGroup>(StatementGrouper.group(listOf(only)).single())

        assertEquals(listOf(only), group.entries)
        assertEquals("250.01", group.totalRedeemed.toApiString())
    }

    @Test
    fun `different requestIds are different groups`() {
        val entries =
            listOf(
                entry("a1", EntryType.Redemption, "10.00", requestId = "req-a"),
                entry("b1", EntryType.Redemption, "20.00", requestId = "req-b"),
                entry("a2", EntryType.Redemption, "30.00", requestId = "req-a"),
            )

        val items = StatementGrouper.group(entries).map { assertIs<StatementItem.RedemptionGroup>(it) }

        assertEquals(listOf("req-a", "req-b"), items.map { it.requestId })
        assertEquals(listOf("40.00", "20.00"), items.map { it.totalRedeemed.toApiString() })
    }

    @Test
    fun `an empty statement has no items`() {
        assertEquals(emptyList(), StatementGrouper.group(emptyList()))
    }
}
