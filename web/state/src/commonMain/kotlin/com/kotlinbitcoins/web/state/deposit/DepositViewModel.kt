package com.kotlinbitcoins.web.state.deposit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.format.AmountInputParser
import com.kotlinbitcoins.web.core.model.DepositResult
import com.kotlinbitcoins.web.core.model.EntryType
import com.kotlinbitcoins.web.core.model.StatementEntry
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.statement.StatementFilter
import com.kotlinbitcoins.web.state.port.AccountGateway
import com.kotlinbitcoins.web.state.port.Announcer
import com.kotlinbitcoins.web.state.port.StatementGateway
import com.kotlinbitcoins.web.state.shared.Loadable
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
 * The valid amount in the field and what the balance would be with it. [balanceAfter] is a display estimate
 * (balance + amount), always labeled "Estimativa": the new balance is the one the API answers. Null while the balance
 * is not loaded.
 */
data class DepositPreview(
    val amount: Money,
    val balanceAfter: Money?,
)

/**
 * State of the deposit page (data-model §4.4): the machine of §4.2 without review, plus the blocks next to the form,
 * each with its own state (FR-042): the [balance], the [preview] of the amount being typed and the newest deposits of
 * the default period of the statement. [attempt] changes on every confirmation, so the page moves the focus to the
 * field again when the same error repeats (FR-046).
 */
data class DepositUiState(
    val operation: OperationState<Money, DepositResult> = OperationState.Editing(""),
    val attempt: Int = 0,
    val balance: Loadable<Money> = Loadable.Loading,
    /** Only while editing or sending a valid amount. */
    val preview: DepositPreview? = null,
    /** Amounts offered as shortcuts to fill the field. */
    val quickAmounts: List<Money> = DepositViewModel.QUICK_AMOUNTS,
    val recentDeposits: Loadable<List<StatementEntry>> = Loadable.Loading,
)

/**
 * Deposit (US3, FR-014): the amount typed in pt-BR is checked locally (W-11) and sent once (FR-043/SC-004). The
 * result shows only what the API answered; a success is announced as assertive (FR-047). A lost response is
 * uncertain and never resent (FR-041). Reloading the page starts again from an empty field. The balance and the
 * newest deposits load when the page opens and are read again after a deposit.
 */
class DepositViewModel(
    private val accountGateway: AccountGateway,
    private val statementGateway: StatementGateway,
    private val announcer: Announcer,
    private val texts: () -> DepositTexts,
    /** Scope of the loads and the deposit; null uses `viewModelScope`. Tests pass a scope that ends with the test. */
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
        this.scope.launch { loadBalance() }
        this.scope.launch { loadRecentDeposits() }
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

    fun onRetryBalance() {
        mutableState.update { it.copy(balance = Loadable.Loading, preview = preview(it.operation, Loadable.Loading)) }
        scope.launch { loadBalance() }
    }

    fun onRetryRecentDeposits() {
        mutableState.update { it.copy(recentDeposits = Loadable.Loading) }
        scope.launch { loadRecentDeposits() }
    }

    private inline fun intent(block: () -> Unit) {
        block()
        sync()
    }

    private fun sync() {
        val operation = machine.state.value
        mutableState.update { it.copy(operation = operation, preview = preview(operation, it.balance)) }
    }

    private suspend fun loadBalance() {
        val balance =
            when (val result = accountGateway.balance()) {
                is Outcome.Success -> Loadable.Loaded(result.value)
                is Outcome.Failure -> Loadable.Failed(result.error)
            }
        mutableState.update { it.copy(balance = balance, preview = preview(it.operation, balance)) }
    }

    private suspend fun loadRecentDeposits() {
        val recent: Loadable<List<StatementEntry>> =
            when (val result = statementGateway.statement(StatementFilter.Default)) {
                is Outcome.Success -> {
                    val deposits =
                        result.value.entries
                            .filter { it.type == EntryType.Deposit }
                            .take(RECENT_DEPOSITS)
                    if (deposits.isEmpty()) Loadable.Empty else Loadable.Loaded(deposits)
                }

                is Outcome.Failure -> {
                    Loadable.Failed(result.error)
                }
            }
        mutableState.update { it.copy(recentDeposits = recent) }
    }

    private suspend fun submit(amount: Money): Outcome<OperationError, DepositResult> {
        val outcome = accountGateway.deposit(amount)
        when (outcome) {
            is Outcome.Success -> {
                mutableState.update { it.copy(balance = Loadable.Loaded(outcome.value.balance)) }
                announcer.assertive(texts().succeeded)
                scope.launch { loadRecentDeposits() }
            }

            is Outcome.Failure -> {
                // The deposit may have been credited: the blocks show what the API has now, never an old value.
                if (outcome.error == OperationError.Uncertain) {
                    scope.launch { loadBalance() }
                    scope.launch { loadRecentDeposits() }
                }
            }
        }
        return outcome
    }

    /** Only a valid amount, being typed or sent, has a preview. */
    private fun preview(
        operation: OperationState<Money, DepositResult>,
        balance: Loadable<Money>,
    ): DepositPreview? {
        val amount =
            when (operation) {
                is OperationState.Editing -> (AmountInputParser.parse(operation.input) as? Outcome.Success)?.value
                is OperationState.Submitting -> operation.amount
                else -> null
            }
        return amount?.let { DepositPreview(it, (balance as? Loadable.Loaded)?.value?.plus(it)) }
    }

    companion object {
        private const val RECENT_DEPOSITS = 3

        /** R$ 100,00, R$ 250,00, R$ 500,00 and R$ 1.000,00. */
        val QUICK_AMOUNTS: List<Money> =
            listOf("100", "250", "500", "1000").mapNotNull { (AmountInputParser.parse(it) as? Outcome.Success)?.value }
    }
}
