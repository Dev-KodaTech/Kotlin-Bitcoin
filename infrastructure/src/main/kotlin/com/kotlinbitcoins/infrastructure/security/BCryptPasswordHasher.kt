package com.kotlinbitcoins.infrastructure.security

import com.kotlinbitcoins.application.port.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/** BCrypt with cost 12 (R-09). Only the hash is ever stored (FR-007). */
class BCryptPasswordHasher : PasswordHasher {
    private val encoder = BCryptPasswordEncoder(COST)

    override fun hash(raw: String): String = encoder.encode(raw)

    override fun matches(
        raw: String,
        hash: String,
    ): Boolean = encoder.matches(raw, hash)

    private companion object {
        const val COST = 12
    }
}
