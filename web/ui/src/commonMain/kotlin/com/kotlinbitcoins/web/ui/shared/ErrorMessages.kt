package com.kotlinbitcoins.web.ui.shared

import androidx.compose.runtime.Composable
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.error_email_in_use
import com.kotlinbitcoins.web.ui.resources.error_email_invalid
import com.kotlinbitcoins.web.ui.resources.error_insufficient_balance
import com.kotlinbitcoins.web.ui.resources.error_invalid_amount
import com.kotlinbitcoins.web.ui.resources.error_invalid_amount_satoshi
import com.kotlinbitcoins.web.ui.resources.error_invalid_credentials
import com.kotlinbitcoins.web.ui.resources.error_invalid_date_range
import com.kotlinbitcoins.web.ui.resources.error_name_required
import com.kotlinbitcoins.web.ui.resources.error_name_too_long
import com.kotlinbitcoins.web.ui.resources.error_no_connection
import com.kotlinbitcoins.web.ui.resources.error_password_too_long
import com.kotlinbitcoins.web.ui.resources.error_password_too_short
import com.kotlinbitcoins.web.ui.resources.error_quote_unavailable
import com.kotlinbitcoins.web.ui.resources.error_redemption_exceeds
import com.kotlinbitcoins.web.ui.resources.error_service_unavailable
import com.kotlinbitcoins.web.ui.resources.error_session_expired
import com.kotlinbitcoins.web.ui.resources.error_uncertain
import com.kotlinbitcoins.web.ui.resources.error_unexpected
import com.kotlinbitcoins.web.ui.resources.error_validation_summary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Where the error happened, when that changes the message. */
enum class ErrorContext {
    General,

    /** INVALID_AMOUNT of a purchase may also mean "below 1 satoshi" (FR-016). */
    Purchase,
}

/**
 * Error → pt-BR message (contracts/error-messages.md). The server `detail` never reaches the screen (SC-003); a
 * VALIDATION_ERROR shows the summary here and each `errors[].message` next to its field.
 */
fun ApiError.messageResources(context: ErrorContext = ErrorContext.General): List<StringResource> =
    when (this) {
        is ApiError.Validation -> {
            listOf(Res.string.error_validation_summary)
        }

        ApiError.InvalidAmount -> {
            if (context == ErrorContext.Purchase) {
                listOf(Res.string.error_invalid_amount, Res.string.error_invalid_amount_satoshi)
            } else {
                listOf(Res.string.error_invalid_amount)
            }
        }

        ApiError.EmailAlreadyInUse -> {
            listOf(Res.string.error_email_in_use)
        }

        ApiError.InvalidCredentials -> {
            listOf(Res.string.error_invalid_credentials)
        }

        ApiError.Unauthorized -> {
            listOf(Res.string.error_session_expired)
        }

        ApiError.InsufficientBalance -> {
            listOf(Res.string.error_insufficient_balance)
        }

        ApiError.RedemptionExceedsAvailable -> {
            listOf(Res.string.error_redemption_exceeds)
        }

        ApiError.InvalidDateRange -> {
            listOf(Res.string.error_invalid_date_range)
        }

        ApiError.QuoteUnavailable -> {
            listOf(Res.string.error_quote_unavailable)
        }

        ApiError.ServiceUnavailable -> {
            listOf(Res.string.error_service_unavailable)
        }

        ApiError.Unexpected -> {
            listOf(Res.string.error_unexpected)
        }

        ApiError.NoConnection -> {
            listOf(Res.string.error_no_connection)
        }
    }

fun OperationError.messageResources(context: ErrorContext = ErrorContext.General): List<StringResource> =
    when (this) {
        is OperationError.Rejected -> error.messageResources(context)
        OperationError.Uncertain -> listOf(Res.string.error_uncertain)
    }

@Composable
fun ApiError.message(context: ErrorContext = ErrorContext.General): String =
    messageResources(context).map { stringResource(it) }.joinToString(" ")

@Composable
fun OperationError.message(context: ErrorContext = ErrorContext.General): String =
    messageResources(context).map { stringResource(it) }.joinToString(" ")

object ErrorMessages {
    /** Messages of the local validations (W-11); checked by ErrorMessagesTest like the API errors. */
    val allLocalValidationMessages: List<StringResource> =
        listOf(
            Res.string.error_name_required,
            Res.string.error_name_too_long,
            Res.string.error_email_invalid,
            Res.string.error_password_too_short,
            Res.string.error_password_too_long,
            Res.string.error_invalid_amount,
            Res.string.error_invalid_date_range,
        )
}
