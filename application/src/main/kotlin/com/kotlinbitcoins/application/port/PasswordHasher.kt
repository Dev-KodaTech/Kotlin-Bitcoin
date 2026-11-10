package com.kotlinbitcoins.application.port

/**
 * One-way password hashing (R-09). Callers only pass passwords of at most [MAX_PASSWORD_BYTES] bytes in UTF-8,
 * the limit of BCrypt; longer input is rejected before reaching this port (FR-003).
 */
interface PasswordHasher {
    fun hash(raw: String): String

    fun matches(
        raw: String,
        hash: String,
    ): Boolean

    companion object {
        const val MAX_PASSWORD_BYTES = 72
    }
}
