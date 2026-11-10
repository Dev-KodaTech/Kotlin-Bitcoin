package com.kotlinbitcoins.infrastructure.support

import com.kotlinbitcoins.domain.account.Account
import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import java.math.BigDecimal
import java.time.Instant

object AccountFixtures {
    val CREATED_AT: Instant = Instant.parse("2026-11-09T11:10:00Z")

    fun email(value: String): Email = (Email.of(value) as Outcome.Success).value

    fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value

    fun account(
        email: String = "ana@example.com",
        balance: String = "0.00",
        id: AccountId = AccountId.random(),
    ): Account = Account(id, "Ana Souza", email(email), "\$2a\$12\$hash", money(balance), CREATED_AT)
}
