package com.kotlinbitcoins.application.account

import com.kotlinbitcoins.application.port.PasswordHasher

/** Password rules of FR-003: at least 8 characters and at most 72 bytes in UTF-8 (the BCrypt limit). */
internal object PasswordPolicy {
    const val MIN_CHARACTERS = 8

    fun violationOf(raw: String): Violation? =
        when {
            raw.isEmpty() -> Violation.REQUIRED
            raw.codePointCount(0, raw.length) < MIN_CHARACTERS -> Violation.TOO_SHORT
            exceedsMaxBytes(raw) -> Violation.TOO_LONG
            else -> null
        }

    fun exceedsMaxBytes(raw: String): Boolean = raw.toByteArray(Charsets.UTF_8).size > PasswordHasher.MAX_PASSWORD_BYTES
}
