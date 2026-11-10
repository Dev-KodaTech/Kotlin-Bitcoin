package com.kotlinbitcoins.domain.account

import com.kotlinbitcoins.domain.shared.Outcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EmailTest {
    @Test
    fun `normalizes with trim and lowercase`() {
        assertThat(Email.of(" ANA@Example.com ")).isEqualTo(Outcome.Success(Email.of("ana@example.com").valueOrNull()))
        assertThat(Email.of(" ANA@Example.com ").valueOrNull()?.value).isEqualTo("ana@example.com")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "ana.example.com", "ana@", "@example.com", "ana@example", "ana @example.com"])
    fun `rejects malformed addresses`(value: String) {
        assertThat(Email.of(value)).isEqualTo(Outcome.Failure(InvalidEmail))
    }

    @Test
    fun `accepts up to 320 characters and rejects more`() {
        val domain = "@example.com"
        val atLimit = "a".repeat(320 - domain.length) + domain

        assertThat(Email.of(atLimit)).isInstanceOf(Outcome.Success::class.java)
        assertThat(Email.of("a$atLimit")).isEqualTo(Outcome.Failure(InvalidEmail))
    }

    private fun Outcome<InvalidEmail, Email>.valueOrNull(): Email? = (this as? Outcome.Success)?.value
}
