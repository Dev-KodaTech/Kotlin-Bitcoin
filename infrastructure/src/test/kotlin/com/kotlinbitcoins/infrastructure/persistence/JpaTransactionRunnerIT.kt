package com.kotlinbitcoins.infrastructure.persistence

import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.TransactionRunner
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.mapError
import com.kotlinbitcoins.infrastructure.persistence.account.AccountJpaEntity
import com.kotlinbitcoins.infrastructure.persistence.account.SpringDataAccountRepository
import com.kotlinbitcoins.infrastructure.support.AccountFixtures
import com.kotlinbitcoins.infrastructure.support.PostgresIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.UUID

class JpaTransactionRunnerIT : PostgresIntegrationTest() {
    @Autowired
    private lateinit var runner: TransactionRunner

    @Autowired
    private lateinit var accounts: AccountRepository

    @Autowired
    private lateinit var springDataAccounts: SpringDataAccountRepository

    private sealed interface TestError {
        data object BusinessRule : TestError

        data class Infrastructure(
            val failure: InfrastructureFailure,
        ) : TestError
    }

    @Test
    fun `failure returned by the block rolls back without throwing`() {
        val account = AccountFixtures.account()

        val result: Outcome<TestError, Unit> =
            runner.run(TestError::Infrastructure) {
                accounts.save(account)
                Outcome.Failure(TestError.BusinessRule)
            }

        assertThat(result).isEqualTo(Outcome.Failure(TestError.BusinessRule))
        assertThat(countAccounts()).isZero()
    }

    @Test
    fun `success returned by the block commits`() {
        val account = AccountFixtures.account()

        val result =
            runner.run(TestError::Infrastructure) {
                accounts.save(account).mapError { TestError.BusinessRule }
            }

        assertThat(result).isEqualTo(Outcome.Success(account))
        assertThat(countAccounts()).isEqualTo(1)
    }

    @Test
    fun `failure during commit rolls back and becomes an infrastructure failure value`() {
        val result: Outcome<TestError, Unit> =
            runner.run(TestError::Infrastructure) {
                // Not flushed here: the CHECK (balance >= 0) violation only shows up when the commit flushes.
                springDataAccounts.save(
                    AccountJpaEntity(
                        id = UUID.randomUUID(),
                        name = "Ana",
                        email = "ana@example.com",
                        passwordHash = "hash",
                        balance = BigDecimal("-1.00"),
                        createdAt = AccountFixtures.CREATED_AT,
                    ),
                )
                Outcome.Success(Unit)
            }

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isInstanceOf(TestError.Infrastructure::class.java)
        assertThat(countAccounts()).isZero()
    }

    private fun countAccounts(): Int = jdbcTemplate.queryForObject("SELECT count(*) FROM accounts", Int::class.java)!!
}
