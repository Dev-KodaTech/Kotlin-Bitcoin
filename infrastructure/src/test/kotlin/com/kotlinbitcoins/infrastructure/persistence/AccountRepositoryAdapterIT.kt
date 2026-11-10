package com.kotlinbitcoins.infrastructure.persistence

import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.SaveAccountError
import com.kotlinbitcoins.application.port.TransactionRunner
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.map
import com.kotlinbitcoins.infrastructure.support.AccountFixtures
import com.kotlinbitcoins.infrastructure.support.PostgresIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AccountRepositoryAdapterIT : PostgresIntegrationTest() {
    @Autowired
    private lateinit var accounts: AccountRepository

    @Autowired
    private lateinit var runner: TransactionRunner

    @Test
    fun `saves and finds an account by id and by email`() {
        val account = AccountFixtures.account()

        assertThat(accounts.save(account)).isEqualTo(Outcome.Success(account))

        assertThat(accounts.findById(account.id)).isEqualTo(Outcome.Success(account))
        assertThat(accounts.findByEmail(account.email)).isEqualTo(Outcome.Success(account))
        assertThat(accounts.existsByEmail(account.email)).isEqualTo(Outcome.Success(true))
    }

    @Test
    fun `returns null and false when the account does not exist`() {
        val email = AccountFixtures.email("nobody@example.com")

        assertThat(accounts.findById(AccountId.random())).isEqualTo(Outcome.Success(null))
        assertThat(accounts.findByEmail(email)).isEqualTo(Outcome.Success(null))
        assertThat(accounts.existsByEmail(email)).isEqualTo(Outcome.Success(false))
    }

    @Test
    fun `saving an existing account updates it`() {
        val account = AccountFixtures.account()
        accounts.save(account)
        val updated = account.copy(balance = AccountFixtures.money("10.00"))

        assertThat(accounts.save(updated)).isEqualTo(Outcome.Success(updated))
        assertThat(accounts.findById(account.id)).isEqualTo(Outcome.Success(updated))
    }

    @Test
    fun `duplicate e-mail becomes EmailTaken without throwing`() {
        accounts.save(AccountFixtures.account(email = "ana@example.com"))

        val result = accounts.save(AccountFixtures.account(email = "ana@example.com"))

        assertThat(result).isEqualTo(Outcome.Failure(SaveAccountError.EmailTaken))
    }

    @Test
    fun `balance keeps scale 2 on a round trip`() {
        val account = AccountFixtures.account(balance = "12345678901234567.89")
        accounts.save(account)

        val loaded = (accounts.findById(account.id) as Outcome.Success).value!!

        assertThat(loaded.balance.amount).isEqualTo(BigDecimal("12345678901234567.89"))
        assertThat(loaded.balance.amount.scale()).isEqualTo(2)
    }

    @Test
    fun `every operation returns an infrastructure failure when the database is unreachable`() {
        val account = AccountFixtures.account()
        accounts.save(account)

        withDatabasePaused {
            assertThat(accounts.save(AccountFixtures.account(email = "other@example.com")))
                .isInstanceOf(Outcome.Failure::class.java)
                .extracting { (it as Outcome.Failure).error }
                .isInstanceOf(SaveAccountError.Infrastructure::class.java)
            assertInfrastructureFailure(accounts.findById(account.id))
            assertInfrastructureFailure(accounts.findByEmail(account.email))
            assertInfrastructureFailure(accounts.existsByEmail(account.email))
            assertInfrastructureFailure(
                runner.run({ it }) { accounts.findByIdForUpdate(account.id) },
            )
        }
    }

    @Test
    fun `findByIdForUpdate makes a second transaction wait for the first to commit`() {
        val account = AccountFixtures.account()
        accounts.save(account)
        val firstHasLock = CountDownLatch(1)
        var firstReleasedAt = 0L
        var secondAcquiredAt = 0L

        val first =
            CompletableFuture.runAsync {
                runner.run({ it }) {
                    accounts.findByIdForUpdate(account.id).map {
                        firstHasLock.countDown()
                        Thread.sleep(HOLD_LOCK_MILLIS)
                        firstReleasedAt = System.nanoTime()
                    }
                }
            }
        assertThat(firstHasLock.await(10, TimeUnit.SECONDS)).isTrue()
        val second =
            CompletableFuture.runAsync {
                runner.run({ it }) {
                    accounts.findByIdForUpdate(account.id).map { secondAcquiredAt = System.nanoTime() }
                }
            }
        CompletableFuture.allOf(first, second).get(30, TimeUnit.SECONDS)

        assertThat(secondAcquiredAt).isGreaterThan(firstReleasedAt)
    }

    private fun assertInfrastructureFailure(result: Outcome<InfrastructureFailure, *>) {
        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isInstanceOf(InfrastructureFailure::class.java)
    }

    private companion object {
        const val HOLD_LOCK_MILLIS = 1_000L
    }
}
