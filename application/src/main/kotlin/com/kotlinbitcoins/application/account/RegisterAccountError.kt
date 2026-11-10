package com.kotlinbitcoins.application.account

import com.kotlinbitcoins.application.port.InfrastructureUnavailable

/** Why a registration field was rejected. The HTTP layer turns it into a Portuguese message. */
enum class Violation {
    REQUIRED,
    INVALID_FORMAT,
    TOO_SHORT,
    TOO_LONG,
}

/** One invalid input field; [field] is the name the client sent (e.g. `email`). */
data class FieldError(
    val field: String,
    val violation: Violation,
)

sealed interface RegisterAccountError {
    /** Every invalid field of the request, reported together (FR-003). */
    data class InvalidInput(
        val fields: List<FieldError>,
    ) : RegisterAccountError

    /** The normalized e-mail already belongs to an account (FR-002). */
    data object EmailAlreadyInUse : RegisterAccountError

    data object ServiceUnavailable : RegisterAccountError, InfrastructureUnavailable
}
