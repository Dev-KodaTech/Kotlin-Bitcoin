package com.kotlinbitcoins.web.state.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Position
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.state.port.AccountGateway
import com.kotlinbitcoins.web.state.port.Announcer
import com.kotlinbitcoins.web.state.port.InvestmentGateway
import com.kotlinbitcoins.web.state.port.PageVisibility
import com.kotlinbitcoins.web.state.port.ProfileGateway
import com.kotlinbitcoins.web.state.port.QuoteGateway
import com.kotlinbitcoins.web.state.shared.AutoRefresher
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.state.shared.QuoteAvailabilityTracker
import com.kotlinbitcoins.web.state.shared.QuoteState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

/** Greeting of the h1 (FR-005): the first name, or "Olá!" when the profile failed or has no name (US2-7). */
sealed interface Greeting {
    data class Named(
        val firstName: String,
    ) : Greeting

    data object Anonymous : Greeting
}

/** Current value of all the investments (FR-010). Every amount comes from the API (Principle II). */
sealed interface PositionTotal {
    data object Loading : PositionTotal

    data class Value(
        val total: Money,
    ) : PositionTotal

    /** QUOTE_UNAVAILABLE: no old value is shown (US2-3). */
    data object DependsOnQuote : PositionTotal

    /** No open investment: the total of the API (R$ 0,00) with the invitation to buy (US2-4). */
    data class NoInvestments(
        val total: Money,
    ) : PositionTotal

    data class Failed(
        val error: ApiError,
    ) : PositionTotal
}

/** State of the dashboard (data-model §4): each block loads and fails alone (FR-013). */
data class DashboardUiState(
    val greeting: Greeting = Greeting.Anonymous,
    val balance: Loadable<Money> = Loadable.Loading,
    val quote: QuoteState = QuoteState.Loading,
    val position: PositionTotal = PositionTotal.Loading,
    val isAutoRefreshPaused: Boolean = false,
)

/**
 * Announced texts, which live in the string resources of :ui. The ViewModel reads them through a provider when it
 * announces: on the web the resources load asynchronously, so the texts of the first composition are empty.
 */
data class DashboardTexts(
    val quoteUnavailable: String,
    val quoteRestored: String,
    val refreshPaused: String,
    val refreshResumed: String,
)

/**
 * Dashboard (US2): profile and balance load once; quote and position refresh every ~10 s while the page is visible
 * and not paused (FR-011/FR-012). Only the availability transitions of the quote and the pause/resume are announced
 * (FR-047): a periodic update changes values without changing the structure or the focus.
 */
@Suppress("LongParameterList")
class DashboardViewModel(
    private val profileGateway: ProfileGateway,
    private val accountGateway: AccountGateway,
    private val quoteGateway: QuoteGateway,
    private val investmentGateway: InvestmentGateway,
    pageVisibility: PageVisibility,
    private val announcer: Announcer,
    private val texts: () -> DashboardTexts,
    refreshInterval: Duration = AutoRefresher.DEFAULT_INTERVAL,
    /** Scope of the loads; null uses `viewModelScope`. Tests pass a scope that ends with the test. */
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(DashboardUiState())
    private val availability =
        QuoteAvailabilityTracker(
            announcer = announcer,
            unavailableText = { texts().quoteUnavailable },
            restoredText = { texts().quoteRestored },
        )
    private val refresher = AutoRefresher(pageVisibility.isVisible, refreshInterval, ::refreshQuoteAndPosition)

    val uiState: StateFlow<DashboardUiState> = mutableState.asStateFlow()

    init {
        this.scope.launch { loadProfile() }
        this.scope.launch { loadBalance() }
        refresher.start(this.scope)
    }

    fun onRetryBalance() {
        mutableState.update { it.copy(balance = Loadable.Loading) }
        this.scope.launch { loadBalance() }
    }

    /** "Tentar de novo" of the quote or of the total: both are read again at once. */
    fun onRetryQuote() {
        this.scope.launch { refreshQuoteAndPosition() }
    }

    fun onToggleAutoRefresh() {
        val paused = !refresher.isPaused.value
        refresher.setPaused(paused)
        mutableState.update { it.copy(isAutoRefreshPaused = paused) }
        announcer.polite(if (paused) texts().refreshPaused else texts().refreshResumed)
    }

    private suspend fun loadProfile() {
        val greeting =
            when (val profile = profileGateway.profile()) {
                is Outcome.Success -> profile.value.firstName?.let(Greeting::Named) ?: Greeting.Anonymous
                is Outcome.Failure -> Greeting.Anonymous
            }
        mutableState.update { it.copy(greeting = greeting) }
    }

    private suspend fun loadBalance() {
        val balance =
            when (val result = accountGateway.balance()) {
                is Outcome.Success -> Loadable.Loaded(result.value)
                is Outcome.Failure -> Loadable.Failed(result.error)
            }
        mutableState.update { it.copy(balance = balance) }
    }

    private suspend fun refreshQuoteAndPosition() =
        coroutineScope {
            launch { refreshQuote() }
            launch { refreshPosition() }
        }

    private suspend fun refreshQuote() {
        val quote =
            when (val result = quoteGateway.current()) {
                is Outcome.Success -> QuoteState.Available(result.value)
                is Outcome.Failure -> quoteFailure(result.error)
            }
        mutableState.update { it.copy(quote = quote) }
        availability.onQuoteState(quote)
    }

    private suspend fun refreshPosition() {
        val position =
            when (val result = investmentGateway.position()) {
                is Outcome.Success -> total(result.value)
                is Outcome.Failure -> positionFailure(result.error)
            }
        mutableState.update { it.copy(position = position) }
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

    private fun positionFailure(error: ApiError): PositionTotal =
        when (error) {
            ApiError.QuoteUnavailable -> PositionTotal.DependsOnQuote

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
            -> PositionTotal.Failed(error)
        }

    private fun total(position: Position): PositionTotal =
        if (position.investments.isEmpty()) {
            PositionTotal.NoInvestments(position.totalCurrentValue)
        } else {
            PositionTotal.Value(position.totalCurrentValue)
        }
}
