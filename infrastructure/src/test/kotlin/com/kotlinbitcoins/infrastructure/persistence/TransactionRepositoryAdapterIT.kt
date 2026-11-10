package com.kotlinbitcoins.infrastructure.persistence

import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.TransactionRepository
import com.kotlinbitcoins.domain.ledger.Transaction
import com.kotlinbitcoins.domain.ledger.TransactionType
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.InvestmentId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import com.kotlinbitcoins.domain.shared.QuotePrice
import com.kotlinbitcoins.domain.shared.RedemptionRequestId
import com.kotlinbitcoins.domain.shared.TransactionId
import com.kotlinbitcoins.infrastructure.support.AccountFixtures
import com.kotlinbitcoins.infrastructure.support.PostgresIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Instant

class TransactionRepositoryAdapterIT : PostgresIntegrationTest() {
    @Autowired
    private lateinit var transactions: TransactionRepository

    @Autowired
    private lateinit var accounts: AccountRepository

    @Test
    fun `saveAll persists a deposit line with exact amount and the instant in UTC`() {
        val account = AccountFixtures.account()
        accounts.save(account)
        val occurredAt = Instant.parse("2026-11-09T11:10:00Z")
        val deposit = Transaction.deposit(account.id, positive("1234.50"), occurredAt)

        assertThat(transactions.saveAll(listOf(deposit))).isEqualTo(Outcome.Success(Unit))

        val row = jdbcTemplate.queryForMap("SELECT * FROM transactions")
        assertThat(row["id"].toString()).isEqualTo(deposit.id.toString())
        assertThat(row["account_id"].toString()).isEqualTo(account.id.toString())
        assertThat(row["type"]).isEqualTo("DEPOSIT")
        assertThat((row["amount"] as BigDecimal).toPlainString()).isEqualTo("1234.50")
        assertThat(row["btc_amount_sats"]).isNull()
        assertThat(row["quote"]).isNull()
        assertThat(row["investment_id"]).isNull()
        assertThat(row["request_id"]).isNull()
        val storedUtc =
            jdbcTemplate.queryForObject(
                "SELECT to_char(occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS') FROM transactions",
                String::class.java,
            )
        assertThat(storedUtc).isEqualTo("2026-11-09T11:10:00")
    }

    @Test
    fun `a line for an unknown account is a failure value, not an exception`() {
        val deposit = Transaction.deposit(AccountFixtures.account().id, positive("10.00"), Instant.now())

        val result = transactions.saveAll(listOf(deposit))

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isInstanceOf(InfrastructureFailure::class.java)
        assertThat(count()).isZero()
    }

    @Test
    fun `database down is a failure value`() {
        val account = AccountFixtures.account()
        accounts.save(account)
        val deposit = Transaction.deposit(account.id, positive("10.00"), Instant.now())

        val result = withDatabasePaused { transactions.saveAll(listOf(deposit)) }

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
    }

    @Test
    fun `findByAccountBetween returns the lines newest first`() {
        val account = AccountFixtures.account()
        accounts.save(account)
        val oldest = Transaction.deposit(account.id, positive("1.00"), Instant.parse("2026-11-01T10:00:00Z"))
        val newest = Transaction.deposit(account.id, positive("3.00"), Instant.parse("2026-11-03T10:00:00Z"))
        val middle = Transaction.deposit(account.id, positive("2.00"), Instant.parse("2026-11-02T10:00:00Z"))
        transactions.saveAll(listOf(oldest, newest, middle))

        val result =
            transactions.findByAccountBetween(
                account.id,
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-12-01T00:00:00Z"),
            )

        assertThat((result as Outcome.Success).value.map { it.id }).containsExactly(newest.id, middle.id, oldest.id)
    }

    @Test
    fun `findByAccountBetween rebuilds every field of a purchase line`() {
        val account = AccountFixtures.account()
        accounts.save(account)
        val investmentId = InvestmentId.random()
        val occurredAt = Instant.parse("2026-11-02T10:00:00Z")
        val purchase =
            Transaction.purchase(
                account.id,
                positive("400.00"),
                BtcAmount(100_000),
                (QuotePrice.of(BigDecimal("400000.00")) as Outcome.Success).value,
                investmentId,
                occurredAt,
            )
        insertInvestment(account.id, investmentId)
        transactions.saveAll(listOf(purchase))

        val result =
            transactions.findByAccountBetween(
                account.id,
                occurredAt.minusSeconds(1),
                occurredAt.plusSeconds(1),
            )

        assertThat((result as Outcome.Success).value).containsExactly(purchase)
    }

    @Test
    fun `findByAccountBetween includes the lower bound and excludes the upper one`() {
        val account = AccountFixtures.account()
        accounts.save(account)
        val from = Instant.parse("2026-11-01T03:00:00Z")
        val to = Instant.parse("2026-11-02T03:00:00Z")
        val before = Transaction.deposit(account.id, positive("1.00"), from.minusSeconds(1))
        val atFrom = Transaction.deposit(account.id, positive("2.00"), from)
        val justInside = Transaction.deposit(account.id, positive("3.00"), to.minusSeconds(1))
        val atTo = Transaction.deposit(account.id, positive("4.00"), to)
        transactions.saveAll(listOf(before, atFrom, justInside, atTo))

        val result = transactions.findByAccountBetween(account.id, from, to)

        assertThat((result as Outcome.Success).value.map { it.id }).containsExactly(justInside.id, atFrom.id)
    }

    @Test
    fun `findByAccountBetween never returns the lines of another account`() {
        val mine = AccountFixtures.account()
        val other = AccountFixtures.account(email = "outra@example.com")
        accounts.save(mine)
        accounts.save(other)
        val at = Instant.parse("2026-11-02T10:00:00Z")
        val myLine = Transaction.deposit(mine.id, positive("1.00"), at)
        transactions.saveAll(listOf(myLine, Transaction.deposit(other.id, positive("9.00"), at)))

        val result = transactions.findByAccountBetween(mine.id, at.minusSeconds(60), at.plusSeconds(60))

        assertThat((result as Outcome.Success).value).containsExactly(myLine)
    }

    @Test
    fun `findByAccountBetween with the database down is a failure value`() {
        val result =
            withDatabasePaused {
                transactions.findByAccountBetween(AccountFixtures.account().id, Instant.now(), Instant.now())
            }

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
    }

    @Test
    fun `sumBtcByTypeBetween adds purchases and redemptions of every account, each type apart`() {
        val ana = AccountFixtures.account()
        val bia = AccountFixtures.account(email = "bia@example.com")
        accounts.save(ana)
        accounts.save(bia)
        val at = Instant.parse("2026-11-09T12:00:00Z")
        transactions.saveAll(
            listOf(
                btcLine(TransactionType.PURCHASE, ana.id, 100_000, at),
                btcLine(TransactionType.PURCHASE, bia.id, 200_000, at.plusSeconds(60)),
                btcLine(TransactionType.REDEMPTION, ana.id, 50_000, at.plusSeconds(120)),
            ),
        )

        val bought = transactions.sumBtcByTypeBetween(TransactionType.PURCHASE, at.minusSeconds(1), at.plusSeconds(600))
        val sold = transactions.sumBtcByTypeBetween(TransactionType.REDEMPTION, at.minusSeconds(1), at.plusSeconds(600))

        assertThat(bought).isEqualTo(Outcome.Success(BtcAmount(300_000)))
        assertThat(sold).isEqualTo(Outcome.Success(BtcAmount(50_000)))
    }

    @Test
    fun `sumBtcByTypeBetween ignores reinvestments and deposits`() {
        val account = AccountFixtures.account()
        accounts.save(account)
        val at = Instant.parse("2026-11-09T12:00:00Z")
        transactions.saveAll(
            listOf(
                btcLine(TransactionType.PURCHASE, account.id, 100_000, at),
                btcLine(TransactionType.REINVESTMENT, account.id, 25_000, at),
                Transaction.deposit(account.id, positive("500.00"), at),
            ),
        )
        val from = at.minusSeconds(1)
        val to = at.plusSeconds(1)

        assertThat(transactions.sumBtcByTypeBetween(TransactionType.PURCHASE, from, to))
            .isEqualTo(Outcome.Success(BtcAmount(100_000)))
        assertThat(transactions.sumBtcByTypeBetween(TransactionType.REDEMPTION, from, to))
            .isEqualTo(Outcome.Success(BtcAmount.ZERO))
    }

    @Test
    fun `sumBtcByTypeBetween without rows is zero`() {
        val result =
            transactions.sumBtcByTypeBetween(
                TransactionType.PURCHASE,
                Instant.parse("2026-11-09T03:00:00Z"),
                Instant.parse("2026-11-10T03:00:00Z"),
            )

        assertThat(result).isEqualTo(Outcome.Success(BtcAmount.ZERO))
    }

    @Test
    fun `sumBtcByTypeBetween respects the local day bounds`() {
        val account = AccountFixtures.account()
        accounts.save(account)
        val from = Instant.parse("2026-11-09T03:00:00Z") // 00:00 of 09/11 in São Paulo
        val to = Instant.parse("2026-11-10T03:00:00Z") // 00:00 of 10/11 in São Paulo
        transactions.saveAll(
            listOf(
                btcLine(TransactionType.PURCHASE, account.id, 1, from.minusSeconds(1)), // 23:59:59 of 08/11 → out
                btcLine(TransactionType.PURCHASE, account.id, 10, from), // 00:00 of 09/11 → in
                btcLine(TransactionType.PURCHASE, account.id, 100, to.minusSeconds(1)), // 23:59:59 of 09/11 → in
                btcLine(TransactionType.PURCHASE, account.id, 1_000, to), // 00:00 of 10/11 → out
            ),
        )

        val result = transactions.sumBtcByTypeBetween(TransactionType.PURCHASE, from, to)

        assertThat(result).isEqualTo(Outcome.Success(BtcAmount(110)))
    }

    @Test
    fun `sumBtcByTypeBetween with the database down is a failure value`() {
        val result =
            withDatabasePaused {
                transactions.sumBtcByTypeBetween(TransactionType.PURCHASE, Instant.now(), Instant.now())
            }

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
    }

    private fun btcLine(
        type: TransactionType,
        accountId: AccountId,
        sats: Long,
        at: Instant,
    ) = Transaction(
        id = TransactionId.random(),
        accountId = accountId,
        type = type,
        amount = (Money.of(BigDecimal("10.00")) as Outcome.Success).value,
        btcAmount = BtcAmount(sats),
        quote = (QuotePrice.of(BigDecimal("400000.00")) as Outcome.Success).value,
        investmentId = null,
        requestId =
            RedemptionRequestId
                .random()
                .takeIf { type == TransactionType.REDEMPTION || type == TransactionType.REINVESTMENT },
        occurredAt = at,
    )

    private fun insertInvestment(
        accountId: AccountId,
        investmentId: InvestmentId,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO investments (id, account_id, purchased_at, fifo_sequence, invested_amount, purchase_quote,
                                     btc_amount_sats, status, origin)
            VALUES (?, ?, now(), nextval('investments_fifo_seq'), 400.00, 400000.00, 100000, 'ACTIVE', 'PURCHASE')
            """.trimIndent(),
            investmentId.value,
            accountId.value,
        )
    }

    private fun count() = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Int::class.java)

    private fun positive(value: String) = (PositiveMoney.of(BigDecimal(value)) as Outcome.Success).value
}
