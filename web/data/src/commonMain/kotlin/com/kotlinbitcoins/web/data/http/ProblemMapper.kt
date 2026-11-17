package com.kotlinbitcoins.web.data.http

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.FieldError
import kotlinx.serialization.Serializable

/**
 * RFC 9457 Problem of the 001 contract. Only what the client uses is read: `detail` is deliberately absent, so the
 * server text can never reach the screen (SC-003).
 */
@Serializable
data class ProblemDto(
    val code: String? = null,
    val errors: List<FieldErrorDto> = emptyList(),
)

@Serializable
data class FieldErrorDto(
    val field: String,
    val message: String,
)

/** The 12 values of `Problem.code` in the contract. */
internal enum class ProblemCode {
    VALIDATION_ERROR,
    INVALID_AMOUNT,
    INVALID_CREDENTIALS,
    UNAUTHORIZED,
    NOT_FOUND,
    EMAIL_ALREADY_IN_USE,
    INSUFFICIENT_BALANCE,
    REDEMPTION_EXCEEDS_AVAILABLE,
    INVALID_DATE_RANGE,
    QUOTE_UNAVAILABLE,
    SERVICE_UNAVAILABLE,
    INTERNAL_ERROR,
}

/** Problem → [ApiError] (contracts/error-messages.md); an unknown code is [ApiError.Unexpected]. */
object ProblemMapper {
    fun map(problem: ProblemDto): ApiError {
        val code = ProblemCode.entries.firstOrNull { it.name == problem.code } ?: return ApiError.Unexpected
        return when (code) {
            ProblemCode.VALIDATION_ERROR -> ApiError.Validation(problem.errors.map { FieldError(it.field, it.message) })
            ProblemCode.INVALID_AMOUNT -> ApiError.InvalidAmount
            ProblemCode.INVALID_CREDENTIALS -> ApiError.InvalidCredentials
            ProblemCode.UNAUTHORIZED -> ApiError.Unauthorized
            ProblemCode.EMAIL_ALREADY_IN_USE -> ApiError.EmailAlreadyInUse
            ProblemCode.INSUFFICIENT_BALANCE -> ApiError.InsufficientBalance
            ProblemCode.REDEMPTION_EXCEEDS_AVAILABLE -> ApiError.RedemptionExceedsAvailable
            ProblemCode.INVALID_DATE_RANGE -> ApiError.InvalidDateRange
            ProblemCode.QUOTE_UNAVAILABLE -> ApiError.QuoteUnavailable
            ProblemCode.SERVICE_UNAVAILABLE -> ApiError.ServiceUnavailable
            ProblemCode.NOT_FOUND, ProblemCode.INTERNAL_ERROR -> ApiError.Unexpected
        }
    }
}
