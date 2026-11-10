package com.kotlinbitcoins.application.ledger

import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.TransactionRepository
import com.kotlinbitcoins.domain.account.Account
import com.kotlinbitcoins.domain.ledger.Transaction
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class GetStatementTest {
    private val now = Instant.parse("2026-11-09T11:10:00Z")
    private val accountId = AccountId.random()
    private val accounts = mockk<AccountRepository>()
    private val transactions = mockk<TransactionRepository>()
    private val clock = Clock { now }

    private val getStatement = GetStatement(accounts, transactions, clock, ZoneId.of("America/Sao_Paulo"))

    @BeforeEach
    fun theAccountExists() {
        every { accounts.findById(accountId) } returns Outcome.Success(mockk<Account>())
    }

    @Test
    fun `without dates it queries the default period of the authenticated account only`() {
        val lines = listOf(deposit("2026-11-09T10:00:00Z"), deposit("2026-11-08T10:00:00Z"))
        every { transactions.findByAccountBetween(accountId, any(), any()) } returns Outcome.Success(lines)

        val result = getStatement.execute(accountId, null, null)

        val statement = (result as Outcome.Success).value
        assertThat(statement.from).isEqualTo(Instant.parse("2026-08-11T03:00:00Z"))
        assertThat(statement.to).isEqualTo(now.plus(1, ChronoUnit.MICROS))
        assertThat(statement.entries).isEqualTo(lines)
        verify(exactly = 1) {
            transactions.findByAccountBetween(accountId, statement.from, statement.to)
        }
    }

    @Test
    fun `with dates it queries the inclusive range in the business zone`() {
        every { transactions.findByAccountBetween(accountId, any(), any()) } returns Outcome.Success(emptyList())

        val result = getStatement.execute(accountId, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-09"))

        val statement = (result as Outcome.Success).value
        assertThat(statement.entries).isEmpty()
        verify {
            transactions.findByAccountBetween(
                accountId,
                Instant.parse("2026-10-01T03:00:00Z"),
                Instant.parse("2026-11-10T03:00:00Z"),
            )
        }
    }

    @Test
    fun `keeps the order given by the repository`() {
        val newer = deposit("2026-11-09T10:00:00Z")
        val older = deposit("2026-11-01T10:00:00Z")
        every { transactions.findByAccountBetween(accountId, any(), any()) } returns
            Outcome.Success(listOf(newer, older))

        val statement = (getStatement.execute(accountId, null, null) as Outcome.Success).value

        assertThat(statement.entries).containsExactly(newer, older)
    }

    @Test
    fun `an invalid range is InvalidDateRange and the repository is not queried`() {
        val result = getStatement.execute(accountId, LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-01"))

        assertThat(result).isEqualTo(Outcome.Failure(StatementError.InvalidDateRange))
        verify(exactly = 0) { transactions.findByAccountBetween(any(), any(), any()) }
    }

    @Test
    fun `failure of the repository is ServiceUnavailable and writes nothing`() {
        every { transactions.findByAccountBetween(accountId, any(), any()) } returns
            Outcome.Failure(InfrastructureFailure("db down"))

        val result = getStatement.execute(accountId, null, null)

        assertThat(result).isEqualTo(Outcome.Failure(StatementError.ServiceUnavailable))
        verify(exactly = 0) { transactions.saveAll(any()) }
    }

    @Test
    fun `an account that does not exist is AccountNotFound and the repository is not queried`() {
        every { accounts.findById(accountId) } returns Outcome.Success(null)

        val result = getStatement.execute(accountId, null, null)

        assertThat(result).isEqualTo(Outcome.Failure(StatementError.AccountNotFound))
        verify(exactly = 0) { transactions.findByAccountBetween(any(), any(), any()) }
    }

    @Test
    fun `failure loading the account is ServiceUnavailable`() {
        every { accounts.findById(accountId) } returns Outcome.Failure(InfrastructureFailure("db down"))

        val result = getStatement.execute(accountId, null, null)

        assertThat(result).isEqualTo(Outcome.Failure(StatementError.ServiceUnavailable))
        verify(exactly = 0) { transactions.findByAccountBetween(any(), any(), any()) }
    }

    private fun deposit(at: String): Transaction =
        Transaction.deposit(
            accountId,
            (PositiveMoney.of(BigDecimal("10.00")) as Outcome.Success).value,
            Instant.parse(at),
        )
}
