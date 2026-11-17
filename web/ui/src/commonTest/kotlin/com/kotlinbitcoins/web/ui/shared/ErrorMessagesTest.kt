package com.kotlinbitcoins.web.ui.shared

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.FieldError
import com.kotlinbitcoins.web.core.error.OperationError
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SC-003: every error has a pt-BR message, and no message carries technical details. */
class ErrorMessagesTest {
    private val apiErrors: List<ApiError> =
        listOf(
            ApiError.Validation(listOf(FieldError("email", "E-mail em formato inválido."))),
            ApiError.InvalidAmount,
            ApiError.EmailAlreadyInUse,
            ApiError.InvalidCredentials,
            ApiError.Unauthorized,
            ApiError.InsufficientBalance,
            ApiError.RedemptionExceedsAvailable,
            ApiError.InvalidDateRange,
            ApiError.QuoteUnavailable,
            ApiError.ServiceUnavailable,
            ApiError.Unexpected,
            ApiError.NoConnection,
        )

    private val operationErrors: List<OperationError> =
        apiErrors.map { OperationError.Rejected(it) } + OperationError.Uncertain

    /** Exhaustive: a new subtype of ApiError breaks the compilation here until it is listed above. */
    private fun listed(error: ApiError): Boolean =
        when (error) {
            is ApiError.Validation,
            ApiError.InvalidAmount,
            ApiError.EmailAlreadyInUse,
            ApiError.InvalidCredentials,
            ApiError.Unauthorized,
            ApiError.InsufficientBalance,
            ApiError.RedemptionExceedsAvailable,
            ApiError.InvalidDateRange,
            ApiError.QuoteUnavailable,
            ApiError.ServiceUnavailable,
            ApiError.Unexpected,
            ApiError.NoConnection,
            -> true
        }

    private fun listed(error: OperationError): Boolean =
        when (error) {
            is OperationError.Rejected -> listed(error.error)
            OperationError.Uncertain -> true
        }

    private val forbidden =
        listOf("code", "HTTP", "exception", "null", "stack", "{") +
            listOf(
                "VALIDATION_ERROR",
                "INVALID_AMOUNT",
                "INVALID_CREDENTIALS",
                "UNAUTHORIZED",
                "NOT_FOUND",
                "EMAIL_ALREADY_IN_USE",
                "INSUFFICIENT_BALANCE",
                "REDEMPTION_EXCEEDS_AVAILABLE",
                "INVALID_DATE_RANGE",
                "QUOTE_UNAVAILABLE",
                "SERVICE_UNAVAILABLE",
                "INTERNAL_ERROR",
            )

    @Test
    fun everyErrorIsListed() {
        assertEquals(12, apiErrors.map { it::class }.toSet().size)
        assertTrue(apiErrors.all(::listed))
        assertTrue(operationErrors.all(::listed))
    }

    @Test
    fun everyApiErrorHasANonEmptyMessage() =
        runTest {
            ErrorContext.entries.forEach { context ->
                apiErrors.forEach { error ->
                    val keys = error.messageResources(context)
                    assertTrue(keys.isNotEmpty(), "$error ($context) has no message")
                    keys.forEach { assertTrue(getString(it).isNotBlank(), "$error ($context): empty string") }
                }
            }
        }

    @Test
    fun everyOperationErrorHasANonEmptyMessage() =
        runTest {
            operationErrors.forEach { error ->
                val keys = error.messageResources(ErrorContext.Purchase)
                assertTrue(keys.isNotEmpty(), "$error has no message")
                keys.forEach { assertTrue(getString(it).isNotBlank(), "$error: empty string") }
            }
        }

    @Test
    fun noErrorTextHasTechnicalDetails() =
        runTest {
            val keys =
                ErrorContext.entries.flatMap { context ->
                    apiErrors.flatMap { it.messageResources(context) } +
                        operationErrors.flatMap { it.messageResources(context) }
                } + ErrorMessages.allLocalValidationMessages
            keys.toSet().forEach { key ->
                val text = getString(key)
                forbidden.forEach { word -> assertFalse(text.contains(word), "\"$text\" contains \"$word\"") }
            }
        }

    @Test
    fun purchaseAddsTheSatoshiComplementOnlyToInvalidAmount() =
        runTest {
            val general = ApiError.InvalidAmount.messageResources(ErrorContext.General).map { getString(it) }
            val purchase = ApiError.InvalidAmount.messageResources(ErrorContext.Purchase).map { getString(it) }
            assertEquals(1, general.size)
            assertEquals(2, purchase.size)
            assertTrue(purchase.last().contains("1 satoshi"))
            assertEquals(
                ApiError.InsufficientBalance.messageResources(ErrorContext.General),
                ApiError.InsufficientBalance.messageResources(ErrorContext.Purchase),
            )
        }
}
