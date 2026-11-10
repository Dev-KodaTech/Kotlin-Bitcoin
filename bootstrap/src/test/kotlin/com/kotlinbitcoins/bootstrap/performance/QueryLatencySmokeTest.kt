package com.kotlinbitcoins.bootstrap.performance

import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

/**
 * SC-006: with seeded data (100 investments, 1 000 transactions, 144 history points) each query answers in at most
 * 2 s at the 95th percentile over 200 calls. A smoke test of the indexes and mappings, not a load test.
 */
class QueryLatencySmokeTest : ApplicationTestBase() {
    @Test
    fun `balance, quote, position, statement, volume and history keep p95 under 2 seconds`() {
        stubQuote(buy = "400000.00", sell = "400000.00")
        val ana = api.register("Ana", "ana@example.com")
        seed(ana)
        val queries =
            mapOf(
                "balance" to "/api/v1/me/balance",
                "quote" to "/api/v1/quotes/current",
                "position" to "/api/v1/me/investments",
                "statement" to "/api/v1/me/statement",
                "volume" to "/api/v1/market/daily-volume",
                "history" to "/api/v1/quotes/history",
            )

        val p95 =
            queries.mapValues { (_, path) ->
                api.get(path, ana).andExpect { status { isOk() } } // warm-up, also proves the data is readable
                val durations = (1..CALLS).map { measure { api.get(path, ana).andExpect { status { isOk() } } } }
                durations.sorted()[(CALLS * 95 + 99) / 100 - 1]
            }

        p95.forEach { (name, duration) ->
            assertThat(duration).describedAs("p95 of $name").isLessThanOrEqualTo(LIMIT)
        }
    }

    private fun measure(block: () -> Unit): Duration {
        val start = System.nanoTime()
        block()
        return Duration.ofNanos(System.nanoTime() - start)
    }

    private fun seed(account: UUID) {
        jdbcTemplate.update(
            """
            INSERT INTO investments (id, account_id, purchased_at, fifo_sequence, invested_amount, purchase_quote,
                                     btc_amount_sats, status, origin)
            SELECT gen_random_uuid(), ?, TIMESTAMPTZ '2026-11-09 11:10:00+00' - (n || ' hours')::interval,
                   nextval('investments_fifo_seq'), 100.00, 400000.00, 25000, 'ACTIVE', 'PURCHASE'
            FROM generate_series(1, $INVESTMENTS) AS n
            """.trimIndent(),
            account,
        )
        jdbcTemplate.update(
            """
            INSERT INTO transactions (id, account_id, type, amount, btc_amount_sats, quote, request_id, occurred_at)
            SELECT gen_random_uuid(), ?,
                   CASE n % 3 WHEN 0 THEN 'DEPOSIT' WHEN 1 THEN 'PURCHASE' ELSE 'REDEMPTION' END,
                   (n % 500) + 1.00,
                   CASE WHEN n % 3 = 0 THEN NULL ELSE 25000 END,
                   CASE WHEN n % 3 = 0 THEN NULL ELSE 400000.00 END,
                   CASE WHEN n % 3 = 2 THEN gen_random_uuid() END,
                   TIMESTAMPTZ '2026-11-09 11:10:00+00' - (n || ' minutes')::interval * 60
            FROM generate_series(1, $TRANSACTIONS) AS n
            """.trimIndent(),
            account,
        )
        jdbcTemplate.update(
            """
            INSERT INTO quote_history (slot_at, buy_price, sell_price, captured_at)
            SELECT TIMESTAMPTZ '2026-11-09 11:10:00+00' - (n || ' minutes')::interval * 10, 399000.00, 400000.00,
                   TIMESTAMPTZ '2026-11-09 11:10:00+00' - (n || ' minutes')::interval * 10
            FROM generate_series(0, ${HISTORY_POINTS - 1}) AS n
            """.trimIndent(),
        )
    }

    private companion object {
        const val INVESTMENTS = 100
        const val TRANSACTIONS = 1_000
        const val HISTORY_POINTS = 144
        const val CALLS = 200
        val LIMIT: Duration = Duration.ofSeconds(2)
    }
}
