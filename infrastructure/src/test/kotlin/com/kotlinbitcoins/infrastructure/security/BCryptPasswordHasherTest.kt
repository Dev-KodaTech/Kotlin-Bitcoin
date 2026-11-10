package com.kotlinbitcoins.infrastructure.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BCryptPasswordHasherTest {
    private val hasher = BCryptPasswordHasher()

    @Test
    fun `hashes with BCrypt cost 12`() {
        assertThat(hasher.hash(PASSWORD)).startsWith("\$2a\$12\$")
    }

    @Test
    fun `the hash never equals the password and is salted`() {
        val first = hasher.hash(PASSWORD)
        val second = hasher.hash(PASSWORD)

        assertThat(first).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD)
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `matches the original password only`() {
        val hash = hasher.hash(PASSWORD)

        assertThat(hasher.matches(PASSWORD, hash)).isTrue()
        assertThat(hasher.matches("another-password", hash)).isFalse()
    }

    private companion object {
        const val PASSWORD = "s3nha-forte"
    }
}
