package com.kotlinbitcoins.infrastructure.persistence

import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.InvestmentRepository
import com.kotlinbitcoins.domain.investment.Investment
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.QuotePrice
import com.kotlinbitcoins.infrastructure.support.AccountFixtures
import com.kotlinbitcoins.infrastructure.support.PostgresIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Instant

class InvestmentRepositoryAdapterIT : PostgresIntegrationTest() {
    @Autowired
    private lateinit var investments: InvestmentRepository

    @Autowired
    private lateinit var accounts: AccountRepository

    private val ana = AccountFixtures.account(email = "ana@example.com")
    private val bia = AccountFixtures.account(email = "bia@example.com")

    @BeforeEach
    fun createAccounts() {
        accounts.save(ana)
        accounts.save(bia)
    }

    @Test
    fun `save persists every column and the quote keeps its exact scale`() {
        val investment =
            investment(ana.id, "2026-11-09T11:10:00Z", 5, "400.00", "400000.12345678", 100_000)

        assertThat(investments.save(investment)).isEqualTo(Outcome.Success(investment))

        val row = jdbcTemplate.queryForMap("SELECT * FROM investments")
        assertThat(row["id"].toString()).isEqualTo(investment.id.toString())
        assertThat(row["account_id"].toString()).isEqualTo(ana.id.toString())
        assertThat(row["fifo_sequence"]).isEqualTo(5L)
        assertThat((row["invested_amount"] as BigDecimal).toPlainString()).isEqualTo("400.00")
        assertThat((row["purchase_quote"] as BigDecimal).toPlainString()).isEqualTo("400000.12345678")
        assertThat(row["btc_amount_sats"]).isEqualTo(100_000L)
        assertThat(row["status"]).isEqualTo("ACTIVE")
        assertThat(row["origin"]).isEqualTo("PURCHASE")
        assertThat(row["origin_investment_id"]).isNull()
        assertThat(row["closed_at"]).isNull()
        val storedUtc =
            jdbcTemplate.queryForObject(
                "SELECT to_char(purchased_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS') FROM investments",
                String::class.java,
            )
        assertThat(storedUtc).isEqualTo("2026-11-09T11:10:00")
    }

    @Test
    fun `nextFifoSequence is strictly increasing`() {
        val first = (investments.nextFifoSequence() as Outcome.Success).value
        val second = (investments.nextFifoSequence() as Outcome.Success).value
        val third = (investments.nextFifoSequence() as Outcome.Success).value

        assertThat(listOf(first, second, third)).isSorted.doesNotHaveDuplicates()
        assertThat(second).isGreaterThan(first)
        assertThat(third).isGreaterThan(second)
    }

    @Test
    fun `findActiveByAccountInFifoOrder orders by purchase date and then by FIFO sequence`() {
        val latest = investment(ana.id, "2026-11-09T12:00:00Z", 1, "10.00", "400000.00", 2_500)
        val sameInstantSecond = investment(ana.id, "2026-11-08T12:00:00Z", 9, "10.00", "400000.00", 2_500)
        val sameInstantFirst = investment(ana.id, "2026-11-08T12:00:00Z", 3, "10.00", "400000.00", 2_500)
        val oldest = investment(ana.id, "2026-11-01T12:00:00Z", 8, "10.00", "400000.00", 2_500)
        listOf(latest, sameInstantSecond, sameInstantFirst, oldest).forEach { investments.save(it) }

        val result = investments.findActiveByAccountInFifoOrder(ana.id)

        val ids = (result as Outcome.Success).value.map { it.id }
        assertThat(ids).containsExactly(oldest.id, sameInstantFirst.id, sameInstantSecond.id, latest.id)
    }

    @Test
    fun `findActiveByAccountInFifoOrder ignores closed investments and other accounts`() {
        val active = investment(ana.id, "2026-11-09T12:00:00Z", 1, "10.00", "400000.00", 2_500)
        val closed = investment(ana.id, "2026-11-08T12:00:00Z", 2, "10.00", "400000.00", 2_500)
        val others = investment(bia.id, "2026-11-07T12:00:00Z", 3, "10.00", "400000.00", 2_500)
        listOf(active, closed, others).forEach { investments.save(it) }
        jdbcTemplate.update("UPDATE investments SET status = 'CLOSED', closed_at = now() WHERE id = ?", closed.id.value)

        val result = investments.findActiveByAccountInFifoOrder(ana.id)

        assertThat((result as Outcome.Success).value.map { it.id }).containsExactly(active.id)
    }

    @Test
    fun `a loaded investment equals the saved one, with the quote shown with at least two decimals`() {
        val investment = investment(ana.id, "2026-11-09T11:10:00Z", 5, "400.00", "400000.10", 100_000)
        investments.save(investment)

        val loaded = (investments.findActiveByAccountInFifoOrder(ana.id) as Outcome.Success).value.single()

        assertThat(loaded).isEqualTo(investment)
        assertThat(loaded.purchaseQuote.value.toPlainString()).isEqualTo("400000.10")
    }

    @Test
    fun `close stores the CLOSED status and the closing instant and the investment leaves the active list`() {
        val investment = investment(ana.id, "2026-11-09T11:10:00Z", 5, "400.00", "400000.00", 100_000)
        investments.save(investment)
        val closed = investment.close(Instant.parse("2026-11-10T09:30:00Z"))

        assertThat(investments.close(closed)).isEqualTo(Outcome.Success(closed))

        val row = jdbcTemplate.queryForMap("SELECT * FROM investments")
        assertThat(row["status"]).isEqualTo("CLOSED")
        val closedUtc =
            jdbcTemplate.queryForObject(
                "SELECT to_char(closed_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS') FROM investments",
                String::class.java,
            )
        assertThat(closedUtc).isEqualTo("2026-11-10T09:30:00")
        assertThat((investments.findActiveByAccountInFifoOrder(ana.id) as Outcome.Success).value).isEmpty()
    }

    @Test
    fun `closing an investment that is not active is a failure value and changes nothing`() {
        val investment = investment(ana.id, "2026-11-09T11:10:00Z", 5, "400.00", "400000.00", 100_000)
        investments.save(investment)
        val closed = investment.close(Instant.parse("2026-11-10T09:30:00Z"))
        investments.close(closed)

        val again = investments.close(investment.close(Instant.parse("2026-11-11T09:30:00Z")))

        assertThat(again).isInstanceOf(Outcome.Failure::class.java)
        val closedUtc =
            jdbcTemplate.queryForObject(
                "SELECT to_char(closed_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS') FROM investments",
                String::class.java,
            )
        assertThat(closedUtc).isEqualTo("2026-11-10T09:30:00")
    }

    @Test
    fun `closing an unknown investment is a failure value`() {
        val ghost = investment(ana.id, "2026-11-09T11:10:00Z", 5, "400.00", "400000.00", 100_000)

        val result = investments.close(ghost.close(Instant.parse("2026-11-10T09:30:00Z")))

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
    }

    @Test
    fun `a reinvestment keeps the original place in the queue and comes before later purchases`() {
        val original = investment(ana.id, "2026-11-01T12:00:00Z", 1, "400.00", "400000.00", 100_000)
        val later = investment(ana.id, "2026-11-05T12:00:00Z", 2, "400.00", "400000.00", 100_000)
        investments.save(original)
        investments.save(later)
        val reinvestment = Investment.reinvestmentOf(original, BtcAmount(25_000))

        investments.close(original.close(Instant.parse("2026-11-09T12:00:00Z")))
        assertThat(investments.save(reinvestment)).isEqualTo(Outcome.Success(reinvestment))

        val active = (investments.findActiveByAccountInFifoOrder(ana.id) as Outcome.Success).value
        assertThat(active.map { it.id }).containsExactly(reinvestment.id, later.id)
        val stored = active.first()
        assertThat(stored).isEqualTo(reinvestment)
        assertThat(stored.originInvestmentId).isEqualTo(original.id)
        assertThat(stored.purchasedAt).isEqualTo(original.purchasedAt)
        assertThat(stored.fifoSequence).isEqualTo(1)
        val row = jdbcTemplate.queryForMap("SELECT * FROM investments WHERE id = ?", reinvestment.id.value)
        assertThat(row["origin"]).isEqualTo("REINVESTMENT")
        assertThat(row["origin_investment_id"].toString()).isEqualTo(original.id.toString())
    }

    @Test
    fun `an investment for an unknown account is a failure value, not an exception`() {
        val orphan = investment(AccountId.random(), "2026-11-09T11:10:00Z", 1, "10.00", "400000.00", 2_500)

        val result = investments.save(orphan)

        assertThat((result as Outcome.Failure).error).isInstanceOf(InfrastructureFailure::class.java)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM investments", Int::class.java)).isZero()
    }

    @Test
    fun `database down is a failure value in every operation`() {
        val investment = investment(ana.id, "2026-11-09T11:10:00Z", 1, "10.00", "400000.00", 2_500)

        withDatabasePaused {
            assertThat(investments.save(investment)).isInstanceOf(Outcome.Failure::class.java)
            assertThat(investments.nextFifoSequence()).isInstanceOf(Outcome.Failure::class.java)
            assertThat(investments.close(investment.close(Instant.now()))).isInstanceOf(Outcome.Failure::class.java)
            assertThat(investments.findActiveByAccountInFifoOrder(ana.id)).isInstanceOf(Outcome.Failure::class.java)
        }
    }

    @Suppress("LongParameterList")
    private fun investment(
        accountId: AccountId,
        purchasedAt: String,
        fifoSequence: Long,
        invested: String,
        quote: String,
        sats: Long,
    ) = Investment.purchase(
        accountId = accountId,
        purchasedAt = Instant.parse(purchasedAt),
        fifoSequence = fifoSequence,
        investedAmount = AccountFixtures.money(invested),
        purchaseQuote = (QuotePrice.of(BigDecimal(quote)) as Outcome.Success).value,
        btcAmount = BtcAmount(sats),
    )
}
