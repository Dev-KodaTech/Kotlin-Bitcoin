package com.kotlinbitcoins.web.state.redemption

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.estimate.RedeemEstimate
import com.kotlinbitcoins.web.core.format.AmountInputParser
import com.kotlinbitcoins.web.core.model.Position
import com.kotlinbitcoins.web.core.model.RedemptionResult
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.state.port.Announcer
import com.kotlinbitcoins.web.state.port.InvestmentGateway
import com.kotlinbitcoins.web.state.port.PageVisibility
import com.kotlinbitcoins.web.state.shared.AutoRefresher
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.state.shared.OperationMachine
import com.kotlinbitcoins.web.state.shared.OperationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

/** Announced texts, read from the string resources of :ui when announced (they load asynchronously on the web). */
data class RedemptionTexts(
    val succeeded: String,
    val refreshPaused: String,
    val refreshResumed: String,
)

/**
 * State of the redemption page (data-model §4.4): the machine of §4.2 with review, the [available] position (total
 * current value, buy quote and its instant) and the labeled [estimate], which exists only while reviewing or sending
 * and is null without a quote. [attempt] changes on every "Revisar resgate", so the page moves the focus to the field
 * again when the same error repeats (FR-046).
 */
data class RedemptionUiState(
    val operation: OperationState<Money, RedemptionResult> = OperationState.Editing(""),
    val available: Loadable<Position> = Loadable.Loading,
    val estimate: RedeemEstimate? = null,
    val isAutoRefreshPaused: Boolean = false,
    val attempt: Int = 0,
) {
    /** "Resgatar tudo" needs an available shown on the page (FR-022). */
    val canRedeemAll: Boolean
        get() = available is Loadable.Loaded

    /** No open investment: the available is R$ 0,00 with an invitation to buy, and sending is still allowed (US6-7). */
    val isEmpty: Boolean
        get() = (available as? Loadable.Loaded)?.value?.investments?.isEmpty() == true
}

/**
 * Redemption (US6, FR-022 to FR-025): the available (`GET /me/investments`, refreshed every ~10 s while visible and
 * not paused) → amount, or "Resgatar tudo" that only fills the field → review with an estimate by the buy quote →
 * confirmation sent once (FR-043/SC-004) → only the result of the API, announced as assertive (FR-047).
 * REDEMPTION_EXCEEDS_AVAILABLE reloads the available (US6-6); a lost response is uncertain and never resent (FR-041).
 */
class RedemptionViewModel(
    private val investmentGateway: InvestmentGateway,
    pageVisibility: PageVisibility,
    private val announcer: Announcer,
    private val texts: () -> RedemptionTexts,
    refreshInterval: Duration = AutoRefresher.DEFAULT_INTERVAL,
    /** Scope of the loads and the redemption; null uses `viewModelScope`. Tests pass a scope ending with the test. */
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(RedemptionUiState())
    private val machine =
        OperationMachine(
            scope = this.scope,
            hasReview = true,
            parse = AmountInputParser::parse,
            submit = ::submit,
        )
    private val refresher = AutoRefresher(pageVisibility.isVisible, refreshInterval, ::loadAvailable)

    /** The field holds the amount put by "Resgatar tudo" until the person types again. */
    private var fromRedeemAll = false

    val uiState: StateFlow<RedemptionUiState> = mutableState.asStateFlow()

    init {
        // The result of a redemption arrives later; every intent below also syncs at once.
        this.scope.launch { machine.state.collect { sync() } }
        refresher.start(this.scope)
    }

    fun onAmountChange(input: String) =
        intent {
            fromRedeemAll = false
            machine.onAmountChange(input)
        }

    /** Fills the field with the available shown now, without confirming (FR-022); also after a refusal (US6-5). */
    fun onRedeemAll() =
        intent {
            val available = (mutableState.value.available as? Loadable.Loaded)?.value ?: return@intent
            if (machine.state.value is OperationState.Rejected) machine.onEdit()
            if (machine.state.value !is OperationState.Editing) return@intent
            machine.onAmountChange(available.totalCurrentValue.toInputString())
            fromRedeemAll = true
        }

    fun onReview() =
        intent {
            if (machine.state.value is OperationState.Editing) mutableState.update { it.copy(attempt = it.attempt + 1) }
            machine.onReview()
        }

    fun onConfirm() = intent { machine.onConfirm() }

    fun onEdit() = intent { machine.onEdit() }

    fun onRetry() = intent { machine.onRetry() }

    fun onNewOperation() =
        intent {
            fromRedeemAll = false
            machine.onNewOperation()
        }

    /** "Tentar de novo" of the available: it is read again at once. */
    fun onRetryAvailable() {
        mutableState.update { it.copy(available = Loadable.Loading) }
        scope.launch { loadAvailable() }
    }

    fun onToggleAutoRefresh() {
        val paused = !refresher.isPaused.value
        refresher.setPaused(paused)
        mutableState.update { it.copy(isAutoRefreshPaused = paused) }
        announcer.polite(if (paused) texts().refreshPaused else texts().refreshResumed)
    }

    private inline fun intent(block: () -> Unit) {
        block()
        sync()
    }

    private fun sync() {
        val operation = machine.state.value
        mutableState.update { it.copy(operation = operation, estimate = estimate(operation, it.available)) }
    }

    private suspend fun loadAvailable() {
        val available =
            when (val result = investmentGateway.position()) {
                is Outcome.Success -> Loadable.Loaded(result.value)
                is Outcome.Failure -> Loadable.Failed(result.error)
            }
        mutableState.update { it.copy(available = available, estimate = estimate(it.operation, available)) }
    }

    private suspend fun submit(amount: Money): Outcome<OperationError, RedemptionResult> {
        val outcome = investmentGateway.redeem(amount)
        when (outcome) {
            is Outcome.Success -> {
                announcer.assertive(texts().succeeded)
                scope.launch { loadAvailable() }
            }

            is Outcome.Failure -> {
                if (outcome.error == OperationError.Rejected(ApiError.RedemptionExceedsAvailable)) loadAvailable()
            }
        }
        return outcome
    }

    /** Only the review and the sending show an estimate, from the buy quote of the available shown now. */
    private fun estimate(
        operation: OperationState<Money, RedemptionResult>,
        available: Loadable<Position>,
    ): RedeemEstimate? {
        val amount =
            when (operation) {
                is OperationState.Reviewing -> operation.amount
                is OperationState.Submitting -> operation.amount
                else -> null
            }
        val position = (available as? Loadable.Loaded)?.value
        return if (amount != null &&
            position != null
        ) {
            RedeemEstimate.of(amount, position.quote, fromRedeemAll)
        } else {
            null
        }
    }
}
