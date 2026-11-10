package com.kotlinbitcoins.domain.account

import com.kotlinbitcoins.domain.shared.Outcome

data object InvalidEmail

/** E-mail normalized with trim + lowercase before uniqueness and login checks (FR-002). */
class Email private constructor(
    val value: String,
) {
    override fun equals(other: Any?): Boolean = other is Email && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 320

        private val FORMAT = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun of(raw: String): Outcome<InvalidEmail, Email> {
            val normalized = raw.trim().lowercase()
            return if (normalized.length <= MAX_LENGTH && FORMAT.matches(normalized)) {
                Outcome.Success(Email(normalized))
            } else {
                Outcome.Failure(InvalidEmail)
            }
        }
    }
}
