package com.kotlinbitcoins.infrastructure.persistence

import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.TransactionRepository
import com.kotlinbitcoins.domain.ledger.Transaction
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
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

    private fun count() = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Int::class.java)

    private fun positive(value: String) = (PositiveMoney.of(BigDecimal(value)) as Outcome.Success).value
}
