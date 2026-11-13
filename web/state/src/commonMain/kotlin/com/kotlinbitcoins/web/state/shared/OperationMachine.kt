package com.kotlinbitcoins.web.state.shared

import com.kotlinbitcoins.web.core.error.AmountInputError
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** States of a financial operation (data-model §4.2). The estimate is not part of it: a result never shows it. */
sealed interface OperationState<out A, out R> {
    data class Editing(
        val input: String,
        val inputError: OperationInputError? = null,
    ) : OperationState<Nothing, Nothing>

    data class Reviewing<out A>(
        val amount: A,
    ) : OperationState<A, Nothing>

    /** Sending: further intents are ignored (single flight, FR-043/SC-004). */
    data class Submitting<out A>(
        val amount: A,
    ) : OperationState<A, Nothing>

    data class Succeeded<out R>(
        val result: R,
    ) : OperationState<Nothing, R>

    data class Rejected<out A>(
        val amount: A,
        val error: ApiError,
    ) : OperationState<A, Nothing>

    /** The transport failed after sending: no retry is offered (FR-041). */
    data object Uncertain : OperationState<Nothing, Nothing>
}

/** Why the amount field shows the INVALID_AMOUNT message. */
sealed interface OperationInputError {
    data class Invalid(
        val reason: AmountInputError,
    ) : OperationInputError

    /** The API refused the amount (INVALID_AMOUNT); in a purchase it may be below 1 satoshi. */
    data object RefusedByApi : OperationInputError
}

/**
 * Generic state machine of deposit (without review), purchase and redemption. The state changes to
 * [OperationState.Submitting] before the gateway is called, so repeated clicks, Enter or taps send once.
 */
class OperationMachine<A, R>(
    private val scope: CoroutineScope,
    private val hasReview: Boolean,
    private val parse: (String) -> Outcome<AmountInputError, A>,
    private val submit: suspend (A) -> Outcome<OperationError, R>,
) {
    private val mutableState = MutableStateFlow<OperationState<A, R>>(OperationState.Editing(""))
    private var lastInput = ""

    val state: StateFlow<OperationState<A, R>> = mutableState.asStateFlow()

    fun onAmountChange(input: String) {
        if (mutableState.value !is OperationState.Editing) return
        lastInput = input
        mutableState.value = OperationState.Editing(input)
    }

    fun onReview() {
        if (!hasReview || mutableState.value !is OperationState.Editing) return
        validated()?.let { mutableState.value = OperationState.Reviewing(it) }
    }

    fun onEdit() {
        val current = mutableState.value
        if (current is OperationState.Reviewing || current is OperationState.Rejected) {
            mutableState.value = OperationState.Editing(lastInput)
        }
    }

    fun onConfirm() {
        when (val current = mutableState.value) {
            is OperationState.Reviewing -> send(current.amount)
            is OperationState.Editing -> if (!hasReview) validated()?.let(::send)
            else -> Unit
        }
    }

    /** "Tentar de novo" after QUOTE_UNAVAILABLE/SERVICE_UNAVAILABLE: a new operation that needs a new confirmation. */
    fun onRetry() {
        val current = mutableState.value as? OperationState.Rejected ?: return
        if (current.error != ApiError.QuoteUnavailable && current.error != ApiError.ServiceUnavailable) return
        mutableState.value =
            if (hasReview) OperationState.Reviewing(current.amount) else OperationState.Editing(lastInput)
    }

    fun onNewOperation() {
        val current = mutableState.value
        if (current is OperationState.Succeeded || current is OperationState.Rejected ||
            current == OperationState.Uncertain
        ) {
            lastInput = ""
            mutableState.value = OperationState.Editing("")
        }
    }

    private fun validated(): A? =
        when (val parsed = parse(lastInput)) {
            is Outcome.Success -> {
                parsed.value
            }

            is Outcome.Failure -> {
                mutableState.value = OperationState.Editing(lastInput, OperationInputError.Invalid(parsed.error))
                null
            }
        }

    private fun send(amount: A) {
        mutableState.value = OperationState.Submitting(amount)
        scope.launch { mutableState.value = toState(amount, submit(amount)) }
    }

    private fun toState(
        amount: A,
        outcome: Outcome<OperationError, R>,
    ): OperationState<A, R> =
        when (outcome) {
            is Outcome.Success -> {
                OperationState.Succeeded(outcome.value)
            }

            is Outcome.Failure -> {
                when (val error = outcome.error) {
                    OperationError.Uncertain -> {
                        OperationState.Uncertain
                    }

                    is OperationError.Rejected -> {
                        if (error.error == ApiError.InvalidAmount) {
                            OperationState.Editing(lastInput, OperationInputError.RefusedByApi)
                        } else {
                            OperationState.Rejected(amount, error.error)
                        }
                    }
                }
            }
        }
}
