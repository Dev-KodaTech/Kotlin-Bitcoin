package com.kotlinbitcoins.web.core.error

/**
 * Typed errors of the API (data-model §3). Each `code` of the contract's `Problem.code` maps to one subtype;
 * the server `detail` is never kept (SC-003).
 */
sealed interface ApiError {
    /** VALIDATION_ERROR: one message per field, already in pt-BR and without internal details (001). */
    data class Validation(
        val fieldErrors: List<FieldError>,
    ) : ApiError

    /** INVALID_AMOUNT. */
    data object InvalidAmount : ApiError

    /** EMAIL_ALREADY_IN_USE. */
    data object EmailAlreadyInUse : ApiError

    /** INVALID_CREDENTIALS. */
    data object InvalidCredentials : ApiError

    /** UNAUTHORIZED: triggers the expired session flow. */
    data object Unauthorized : ApiError

    /** INSUFFICIENT_BALANCE. */
    data object InsufficientBalance : ApiError

    /** REDEMPTION_EXCEEDS_AVAILABLE. */
    data object RedemptionExceedsAvailable : ApiError

    /** INVALID_DATE_RANGE. */
    data object InvalidDateRange : ApiError

    /** QUOTE_UNAVAILABLE. */
    data object QuoteUnavailable : ApiError

    /** SERVICE_UNAVAILABLE. */
    data object ServiceUnavailable : ApiError

    /** NOT_FOUND, INTERNAL_ERROR, unknown code, or status/body outside the contract. */
    data object Unexpected : ApiError

    /** Transport failure on a read, login or registration. */
    data object NoConnection : ApiError
}

data class FieldError(
    val field: String,
    val message: String,
)
