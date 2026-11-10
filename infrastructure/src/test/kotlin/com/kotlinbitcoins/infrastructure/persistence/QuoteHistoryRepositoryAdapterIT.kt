package com.kotlinbitcoins.infrastructure.persistence

import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.QuoteHistoryRepository
import com.kotlinbitcoins.domain.quote.QuoteSnapshot
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import com.kotlinbitcoins.infrastructure.support.PostgresIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Instant

class QuoteHistoryRepositoryAdapterIT : PostgresIntegrationTest() {
    @Autowired
    private lateinit var history: QuoteHistoryRepository

    @Test
    fun `saveIfAbsent stores the snapshot and reports that it inserted`() {
        val snapshot = snapshot("2026-11-09T11:10:00Z", buy = "399000.00", sell = "400000.12345678")

        assertThat(history.saveIfAbsent(snapshot)).isEqualTo(Outcome.Success(true))

        val row = jdbcTemplate.queryForMap("SELECT * FROM quote_history")
        assertThat((row["buy_price"] as BigDecimal).compareTo(BigDecimal("399000.00"))).isZero()
        assertThat((row["sell_price"] as BigDecimal).compareTo(BigDecimal("400000.12345678"))).isZero()
        val slotUtc =
            jdbcTemplate.queryForObject(
                "SELECT to_char(slot_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS') FROM quote_history",
                String::class.java,
            )
        assertThat(slotUtc).isEqualTo("2026-11-09T11:10:00")
    }

    @Test
    fun `saveIfAbsent twice in the same slot keeps the first one`() {
        val first = snapshot("2026-11-09T11:10:00Z", buy = "399000.00", sell = "400000.00")
        val second = snapshot("2026-11-09T11:10:00Z", buy = "1.00", sell = "2.00")

        assertThat(history.saveIfAbsent(first)).isEqualTo(Outcome.Success(true))
        assertThat(history.saveIfAbsent(second)).isEqualTo(Outcome.Success(false))

        val rows = jdbcTemplate.queryForList("SELECT buy_price, sell_price FROM quote_history")
        assertThat(rows).hasSize(1)
        assertThat((rows.single()["buy_price"] as BigDecimal).compareTo(BigDecimal("399000.00"))).isZero()
    }

    @Test
    fun `findSince is exclusive on the lower bound, ascending, and keeps the scale of the source`() {
        val since = Instant.parse("2026-11-09T10:00:00Z")
        history.saveIfAbsent(snapshot("2026-11-09T10:20:00Z", buy = "399020.00", sell = "400020.00"))
        history.saveIfAbsent(snapshot("2026-11-09T10:00:00Z", buy = "399000.00", sell = "400000.00"))
        history.saveIfAbsent(snapshot("2026-11-09T10:10:00Z", buy = "399010.50", sell = "400010.12345678"))
        history.saveIfAbsent(snapshot("2026-11-09T09:50:00Z", buy = "398990.00", sell = "399990.00"))

        val found = (history.findSince(since) as Outcome.Success).value

        assertThat(found.map { it.slotAt }).containsExactly(
            Instant.parse("2026-11-09T10:10:00Z"),
            Instant.parse("2026-11-09T10:20:00Z"),
        )
        assertThat(found[0].buyPrice.value.toPlainString()).isEqualTo("399010.50")
        assertThat(found[0].sellPrice.value.toPlainString()).isEqualTo("400010.12345678")
        assertThat(found[1].buyPrice.value.toPlainString()).isEqualTo("399020.00")
        assertThat(found[0].capturedAt).isEqualTo(Instant.parse("2026-11-09T10:10:01Z"))
    }

    @Test
    fun `findSince on an empty table is an empty list`() {
        val found = history.findSince(Instant.parse("2026-11-09T10:00:00Z"))

        assertThat(found).isEqualTo(Outcome.Success(emptyList<QuoteSnapshot>()))
    }

    @Test
    fun `deleteOlderThan removes only the snapshots before the limit and counts them`() {
        val limit = Instant.parse("2026-08-11T06:00:00Z")
        history.saveIfAbsent(snapshot("2026-08-10T05:50:00Z", buy = "1.00", sell = "1.00"))
        history.saveIfAbsent(snapshot("2026-08-11T05:50:00Z", buy = "2.00", sell = "2.00"))
        history.saveIfAbsent(snapshot("2026-08-11T06:00:00Z", buy = "3.00", sell = "3.00"))
        history.saveIfAbsent(snapshot("2026-11-09T11:10:00Z", buy = "4.00", sell = "4.00"))

        val removed = history.deleteOlderThan(limit)

        assertThat(removed).isEqualTo(Outcome.Success(2))
        val remaining = jdbcTemplate.queryForList("SELECT buy_price FROM quote_history ORDER BY slot_at")
        assertThat(remaining.map { (it["buy_price"] as BigDecimal).toInt() }).containsExactly(3, 4)
    }

    @Test
    fun `an unreachable database is a failure value in every operation`() {
        val snapshot = snapshot("2026-11-09T11:10:00Z", buy = "399000.00", sell = "400000.00")

        withDatabasePaused {
            assertThat(history.saveIfAbsent(snapshot)).isInstanceOf(Outcome.Failure::class.java)
            assertThat(history.findSince(Instant.parse("2026-11-08T11:10:00Z")))
                .isInstanceOf(Outcome.Failure::class.java)
            assertThat(history.deleteOlderThan(Instant.parse("2026-08-11T11:10:00Z")))
                .isInstanceOf(Outcome.Failure::class.java)
            assertThat((history.findSince(Instant.now()) as Outcome.Failure).error)
                .isInstanceOf(InfrastructureFailure::class.java)
        }
    }

    private fun snapshot(
        slotAt: String,
        buy: String,
        sell: String,
    ): QuoteSnapshot {
        val slot = Instant.parse(slotAt)
        return QuoteSnapshot(slot, price(buy), price(sell), slot.plusSeconds(1))
    }

    private fun price(value: String): QuotePrice = (QuotePrice.of(BigDecimal(value)) as Outcome.Success).value
}
