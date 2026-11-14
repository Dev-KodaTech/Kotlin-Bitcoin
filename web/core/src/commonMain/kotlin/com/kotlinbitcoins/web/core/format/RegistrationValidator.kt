package com.kotlinbitcoins.web.core.format

import com.kotlinbitcoins.web.core.error.Outcome

/** Fields of the registration form, in form order (the order of the errors and of the focus). */
enum class RegistrationField { Name, Email, Password }

/** Why a registration field was refused before sending (FR-001, contracts/error-messages.md). */
sealed interface RegistrationError {
    val field: RegistrationField

    data object NameBlank : RegistrationError {
        override val field = RegistrationField.Name
    }

    data object NameTooLong : RegistrationError {
        override val field = RegistrationField.Name
    }

    data object EmailInvalid : RegistrationError {
        override val field = RegistrationField.Email
    }

    data object PasswordTooShort : RegistrationError {
        override val field = RegistrationField.Password
    }

    data object PasswordTooLong : RegistrationError {
        override val field = RegistrationField.Password
    }
}

/** Input that passed the local validation: [name] and [email] trimmed, [password] as typed. */
data class ValidRegistration(
    val name: String,
    val email: String,
    val password: String,
) {
    override fun toString(): String = "ValidRegistration(name=$name, email=$email, password=****)"
}

/**
 * Local validation of the registration (FR-001), with the same rules as the 001: `Account` (trimmed name, 1 to 200
 * UTF-16 units), `Email` (trimmed, at most 320 characters, `^[^@\s]+@[^@\s]+\.[^@\s]+$`) and `PasswordPolicy` (at
 * least 8 code points, at most 72 bytes in UTF-8). The API stays the authority; this only avoids a useless request.
 */
object RegistrationValidator {
    private const val MAX_NAME_LENGTH = 200
    private const val MAX_EMAIL_LENGTH = 320
    private const val MIN_PASSWORD_CODE_POINTS = 8
    private const val MAX_PASSWORD_BYTES = 72
    private val EMAIL_FORMAT = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** Every error at once, in the order name, email, password. */
    fun validate(
        name: String,
        email: String,
        password: String,
    ): Outcome<List<RegistrationError>, ValidRegistration> {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        val errors =
            listOfNotNull(
                nameError(trimmedName),
                emailError(trimmedEmail),
                passwordError(password),
            )
        return if (errors.isEmpty()) {
            Outcome.Success(ValidRegistration(trimmedName, trimmedEmail, password))
        } else {
            Outcome.Failure(errors)
        }
    }

    private fun nameError(trimmed: String): RegistrationError? =
        when {
            trimmed.isEmpty() -> RegistrationError.NameBlank
            trimmed.length > MAX_NAME_LENGTH -> RegistrationError.NameTooLong
            else -> null
        }

    private fun emailError(trimmed: String): RegistrationError? =
        when {
            trimmed.length > MAX_EMAIL_LENGTH -> RegistrationError.EmailInvalid
            !EMAIL_FORMAT.matches(trimmed) -> RegistrationError.EmailInvalid
            else -> null
        }

    private fun passwordError(password: String): RegistrationError? =
        when {
            password.codePointCount() < MIN_PASSWORD_CODE_POINTS -> RegistrationError.PasswordTooShort
            password.encodeToByteArray().size > MAX_PASSWORD_BYTES -> RegistrationError.PasswordTooLong
            else -> null
        }

    /** A surrogate pair is one code point, like `String.codePointCount` of the JVM. */
    private fun String.codePointCount(): Int {
        var count = 0
        var index = 0
        while (index < length) {
            val pair = this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()
            index += if (pair) 2 else 1
            count++
        }
        return count
    }
}
