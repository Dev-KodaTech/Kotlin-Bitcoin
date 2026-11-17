package com.kotlinbitcoins.web.state.deposit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.format.AmountInputParser
import com.kotlinbitcoins.web.core.model.DepositResult
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.state.port.AccountGateway
import com.kotlinbitcoins.web.state.port.Announcer
import com.kotlinbitcoins.web.state.shared.OperationMachine
import com.kotlinbitcoins.web.state.shared.OperationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Announced texts, read from the string resources of :ui when announced (they load asynchronously on the web). */
data class DepositTexts(
    val succeeded: String,
)

/**
 * State of the deposit page (data-model §4.4): the machine of §4.2 without review. [attempt] changes on every
 * confirmation, so the page moves the focus to the field again when the same error repeats (FR-046).
 */
data class DepositUiState(
    val operation: OperationState<Money, DepositResult> = OperationState.Editing(""),
    val attempt: Int = 0,
)

/**
 * Deposit (US3, FR-014): the amount typed in pt-BR is checked locally (W-11) and sent once (FR-043/SC-004). The
 * result shows only what the API answered; a success is announced as assertive (FR-047). A lost response is
 * uncertain and never resent (FR-041). Reloading the page starts again from an empty field.
 */
class DepositViewModel(
    private val accountGateway: AccountGateway,
    private val announcer: Announcer,
    private val texts: () -> DepositTexts,
    /** Scope of the deposit; null uses `viewModelScope`. Tests pass a scope that ends with the test. */
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(DepositUiState())
    private val machine =
        OperationMachine(
            scope = this.scope,
            hasReview = false,
            parse = AmountInputParser::parse,
            submit = ::submit,
        )

    val uiState: StateFlow<DepositUiState> = mutableState.asStateFlow()

    init {
        // The result of a submission arrives later; every intent below also syncs at once.
        this.scope.launch { machine.state.collect { sync() } }
    }

    fun onAmountChange(input: String) = intent { machine.onAmountChange(input) }

    fun onConfirm() =
        intent {
            if (machine.state.value is OperationState.Editing) mutableState.update { it.copy(attempt = it.attempt + 1) }
            machine.onConfirm()
        }

    fun onRetry() = intent { machine.onRetry() }

    fun onEdit() = intent { machine.onEdit() }

    fun onNewOperation() = intent { machine.onNewOperation() }

    private inline fun intent(block: () -> Unit) {
        block()
        sync()
    }

    private fun sync() = mutableState.update { it.copy(operation = machine.state.value) }

    private suspend fun submit(amount: Money) =
        accountGateway.deposit(amount).also { outcome ->
            if (outcome is Outcome.Success) announcer.assertive(texts().succeeded)
        }
}
