package com.kotlinbitcoins.web.state.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.estimate.BuyEstimate
import com.kotlinbitcoins.web.core.format.AmountInputParser
import com.kotlinbitcoins.web.core.model.PurchaseResult
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.state.port.AccountGateway
import com.kotlinbitcoins.web.state.port.Announcer
import com.kotlinbitcoins.web.state.port.InvestmentGateway
import com.kotlinbitcoins.web.state.port.PageVisibility
import com.kotlinbitcoins.web.state.port.QuoteGateway
import com.kotlinbitcoins.web.state.shared.AutoRefresher
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.state.shared.OperationMachine
import com.kotlinbitcoins.web.state.shared.OperationState
import com.kotlinbitcoins.web.state.shared.QuoteState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

/** Announced texts, read from the string resources of :ui when announced (they load asynchronously on the web). */
data class PurchaseTexts(
    val succeeded: String,
    val refreshPaused: String,
    val refreshResumed: String,
)

/**
 * State of the purchase page (data-model §4.4): the machine of §4.2 with review, the balance, the quote and the
 * labeled [estimate]. The estimate exists only while reviewing or sending, never on a result (Edge Case), and is null
 * when the quote is not available (US4-7). [attempt] changes on every "Revisar compra", so the page moves the focus
 * to the field again when the same error repeats (FR-046).
 */
data class PurchaseUiState(
    val operation: OperationState<Money, PurchaseResult> = OperationState.Editing(""),
    val balance: Loadable<Money> = Loadable.Loading,
    val quote: QuoteState = QuoteState.Loading,
    val estimate: BuyEstimate? = null,
    val isAutoRefreshPaused: Boolean = false,
    val attempt: Int = 0,
)

/**
 * Purchase (US4, FR-015 to FR-017): amount → review with an estimate recalculated on every quote → confirmation sent
 * once (FR-043/SC-004) → only the result of the API, announced as assertive (FR-047). The quote refreshes every ~10 s
 * only while reviewing, visible and not paused (W-10). INSUFFICIENT_BALANCE reloads the balance (US4-4); a lost
 * response is uncertain and never resent (FR-041). Reloading the page starts again from an empty field.
 */
@Suppress("LongParameterList")
class PurchaseViewModel(
    private val accountGateway: AccountGateway,
    private val quoteGateway: QuoteGateway,
    private val investmentGateway: InvestmentGateway,
    pageVisibility: PageVisibility,
    private val announcer: Announcer,
    private val texts: () -> PurchaseTexts,
    refreshInterval: Duration = AutoRefresher.DEFAULT_INTERVAL,
    /** Scope of the loads and the purchase; null uses `viewModelScope`. Tests pass a scope that ends with the test. */
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(PurchaseUiState())
    private val machine =
        OperationMachine(
            scope = this.scope,
            hasReview = true,
            parse = AmountInputParser::parse,
            submit = ::submit,
        )
    private val reviewing = MutableStateFlow(false)

    /** The refresher runs only while the review is visible: [reviewing] and the page visibility. */
    private val refresher =
        AutoRefresher(
            visible =
                combine(pageVisibility.isVisible, reviewing) { visible, inReview -> visible && inReview }
                    .stateIn(this.scope, SharingStarted.Eagerly, false),
            interval = refreshInterval,
            refresh = ::refreshQuote,
        )

    val uiState: StateFlow<PurchaseUiState> = mutableState.asStateFlow()

    init {
        // The result of a purchase arrives later; every intent below also syncs at once.
        this.scope.launch { machine.state.collect { sync() } }
        this.scope.launch { loadBalance() }
        refresher.start(this.scope)
    }

    fun onAmountChange(input: String) = intent { machine.onAmountChange(input) }

    fun onReview() =
        intent {
            if (machine.state.value is OperationState.Editing) mutableState.update { it.copy(attempt = it.attempt + 1) }
            machine.onReview()
        }

    fun onConfirm() = intent { machine.onConfirm() }

    fun onEdit() = intent { machine.onEdit() }

    fun onRetry() = intent { machine.onRetry() }

    fun onNewOperation() = intent { machine.onNewOperation() }

    fun onRetryBalance() {
        mutableState.update { it.copy(balance = Loadable.Loading) }
        scope.launch { loadBalance() }
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
        reviewing.value = operation is OperationState.Reviewing
        mutableState.update { it.copy(operation = operation, estimate = estimate(operation, it.quote)) }
    }

    private suspend fun refreshQuote() {
        val quote =
            when (val result = quoteGateway.current()) {
                is Outcome.Success -> QuoteState.Available(result.value)
                is Outcome.Failure -> quoteFailure(result.error)
            }
        mutableState.update { it.copy(quote = quote, estimate = estimate(it.operation, quote)) }
    }

    private suspend fun loadBalance() {
        val balance =
            when (val result = accountGateway.balance()) {
                is Outcome.Success -> Loadable.Loaded(result.value)
                is Outcome.Failure -> Loadable.Failed(result.error)
            }
        mutableState.update { it.copy(balance = balance) }
    }

    private suspend fun submit(amount: Money): Outcome<OperationError, PurchaseResult> {
        val outcome = investmentGateway.purchase(amount)
        when (outcome) {
            is Outcome.Success -> {
                mutableState.update { it.copy(balance = Loadable.Loaded(outcome.value.balance)) }
                announcer.assertive(texts().succeeded)
            }

            is Outcome.Failure -> {
                if (outcome.error == OperationError.Rejected(ApiError.InsufficientBalance)) loadBalance()
            }
        }
        return outcome
    }

    private fun quoteFailure(error: ApiError): QuoteState =
        when (error) {
            ApiError.QuoteUnavailable -> QuoteState.Unavailable

            is ApiError.Validation,
            ApiError.InvalidAmount,
            ApiError.EmailAlreadyInUse,
            ApiError.InvalidCredentials,
            ApiError.Unauthorized,
            ApiError.InsufficientBalance,
            ApiError.RedemptionExceedsAvailable,
            ApiError.InvalidDateRange,
            ApiError.ServiceUnavailable,
            ApiError.Unexpected,
            ApiError.NoConnection,
            -> QuoteState.Failed(error)
        }

    /** Only the review and the sending show an estimate, from the quote available now. */
    private fun estimate(
        operation: OperationState<Money, PurchaseResult>,
        quote: QuoteState,
    ): BuyEstimate? {
        val amount =
            when (operation) {
                is OperationState.Reviewing -> operation.amount
                is OperationState.Submitting -> operation.amount
                else -> return null
            }
        return (quote as? QuoteState.Available)?.let { BuyEstimate.of(amount, it.quote) }
    }
}
