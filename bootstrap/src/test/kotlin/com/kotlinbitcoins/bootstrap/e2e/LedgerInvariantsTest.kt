package com.kotlinbitcoins.bootstrap.e2e

import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.util.Random
import java.util.UUID

/**
 * Accounting invariants after a long random sequence of deposits, purchases and redemptions with moving quotes
 * (fixed seed, so a failure is reproducible):
 * - SC-002: balance = Σ DEPOSIT − Σ PURCHASE + Σ REDEMPTION;
 * - SC-003: Σ satoshis of the active investments = Σ satoshis of PURCHASE − Σ satoshis of REDEMPTION.
 */
class LedgerInvariantsTest : ApplicationTestBase() {
    private val random = Random(SEED)
    private lateinit var accounts: List<UUID>

    @Test
    fun `balances and satoshis stay consistent with the ledger after a random sequence`() {
        accounts = (1..ACCOUNTS).map { api.register("Cliente $it", "cliente$it@example.com") }
        var accepted = 0

        repeat(STEPS) {
            moveQuote()
            val account = accounts[random.nextInt(ACCOUNTS)]
            accepted += if (randomOperation(account)) 1 else 0
        }

        assertThat(accepted).describedAs("operations accepted").isGreaterThan(STEPS / 2)
        assertThat(count("PURCHASE")).isPositive()
        assertThat(count("REDEMPTION")).isPositive()
        accounts.forEach { assertBalanceMatchesLedger(it) }
        assertActiveSatoshisMatchLedger()
    }

    /** Returns whether the operation was accepted (a rejected one — 422 — must change nothing). */
    private fun randomOperation(account: UUID): Boolean =
        when (random.nextInt(OPERATION_KINDS)) {
            0 -> api.depositOk(account, randomAmount(MAX_DEPOSIT_CENTS)).let { true }
            1, 2 -> purchase(account)
            else -> redeem(account)
        }

    private fun purchase(account: UUID): Boolean {
        val balance = BigDecimal(api.balance(account))
        if (balance.signum() == 0) return false
        val amount = randomAmountUpTo(balance)
        api.purchaseOk(account, amount)
        return true
    }

    private fun redeem(account: UUID): Boolean {
        val total = BigDecimal(api.json("/api/v1/me/investments", account)["totalCurrentValue"].asText())
        if (total.signum() == 0) return false
        api.redeemOk(account, randomAmountUpTo(total))
        return true
    }

    /** New prices for the next step; the clock moves past the 10 s validity of the quote cache. */
    private fun moveQuote() {
        val buyCents = (MIN_PRICE + random.nextInt(PRICE_RANGE)) * CENTS + random.nextInt(CENTS.toInt())
        val sellCents = buyCents + random.nextInt(SPREAD) * CENTS
        stubQuote(buy = reais(buyCents), sell = reais(sellCents))
        clock.advance(Duration.ofSeconds(QUOTE_STEP_SECONDS))
    }

    private fun randomAmount(maxCents: Int): String = reais(1L + random.nextInt(maxCents))

    /** Between 0.01 and [limit] (inclusive); one time in [FULL_AMOUNT_ONE_IN] exactly the limit. */
    private fun randomAmountUpTo(limit: BigDecimal): String {
        val limitCents = limit.movePointRight(2).longValueExact()
        val cents =
            if (random.nextInt(FULL_AMOUNT_ONE_IN) ==
                0
            ) {
                limitCents
            } else {
                minOf(limitCents, 1 + (random.nextDouble() * limitCents).toLong())
            }
        return reais(cents)
    }

    private fun reais(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

    private fun assertBalanceMatchesLedger(account: UUID) {
        val sums =
            jdbcTemplate
                .queryForList(
                    "SELECT type, SUM(amount) AS total FROM transactions WHERE account_id = ? GROUP BY type",
                    account,
                ).associate { it["type"] as String to it["total"] as BigDecimal }

        fun sum(type: String) = sums[type] ?: BigDecimal.ZERO
        val expected = sum("DEPOSIT") - sum("PURCHASE") + sum("REDEMPTION")

        assertThat(BigDecimal(api.balance(account))).describedAs("balance of $account").isEqualByComparingTo(expected)
        assertThat(expected.signum()).isGreaterThanOrEqualTo(0)
    }

    private fun assertActiveSatoshisMatchLedger() {
        val active = scalar("SELECT COALESCE(SUM(btc_amount_sats), 0) FROM investments WHERE status = 'ACTIVE'")
        val bought = scalar("SELECT COALESCE(SUM(btc_amount_sats), 0) FROM transactions WHERE type = 'PURCHASE'")
        val sold = scalar("SELECT COALESCE(SUM(btc_amount_sats), 0) FROM transactions WHERE type = 'REDEMPTION'")

        assertThat(active).describedAs("active satoshis").isEqualTo(bought - sold)
        accounts.forEach { account ->
            val perAccountActive =
                jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(btc_amount_sats), 0) FROM investments " +
                        "WHERE status = 'ACTIVE' AND account_id = ?",
                    Long::class.java,
                    account,
                )!!
            val perAccountLedger =
                jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(CASE type WHEN 'PURCHASE' THEN btc_amount_sats " +
                        "WHEN 'REDEMPTION' THEN -btc_amount_sats END), 0) FROM transactions WHERE account_id = ?",
                    Long::class.java,
                    account,
                )!!
            assertThat(perAccountActive).describedAs("active satoshis of $account").isEqualTo(perAccountLedger)
        }
    }

    private fun scalar(sql: String): Long = jdbcTemplate.queryForObject(sql, Long::class.java)!!

    private fun count(type: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions WHERE type = ?", Int::class.java, type)!!

    private companion object {
        const val SEED = 20_261_109L
        const val ACCOUNTS = 3
        const val STEPS = 120
        const val OPERATION_KINDS = 5
        const val MAX_DEPOSIT_CENTS = 300_000
        const val MIN_PRICE = 150_000L
        const val PRICE_RANGE = 350_000
        const val CENTS = 100L
        const val SPREAD = 3_000
        const val QUOTE_STEP_SECONDS = 11L
        const val FULL_AMOUNT_ONE_IN = 4
    }
}
