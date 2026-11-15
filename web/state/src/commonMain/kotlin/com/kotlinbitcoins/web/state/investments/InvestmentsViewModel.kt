package com.kotlinbitcoins.web.state.investments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Position
import com.kotlinbitcoins.web.state.port.Announcer
import com.kotlinbitcoins.web.state.port.InvestmentGateway
import com.kotlinbitcoins.web.state.port.PageVisibility
import com.kotlinbitcoins.web.state.shared.AutoRefresher
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.state.shared.QuoteAvailabilityTracker
import com.kotlinbitcoins.web.state.shared.QuoteState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * State of "Meus investimentos" (data-model §4). [position] is [Loadable.Empty] when there is no open investment
 * (US5-4). QUOTE_UNAVAILABLE is its own state ([isQuoteUnavailable]), with "Tentar de novo" and no old values (US5-5).
 */
data class InvestmentsUiState(
    val position: Loadable<Position> = Loadable.Loading,
    val isAutoRefreshPaused: Boolean = false,
) {
    val isQuoteUnavailable: Boolean
        get() = position == Loadable.Failed(ApiError.QuoteUnavailable)
}

/** Announced texts, read from the string resources of :ui when announced (they load asynchronously on the web). */
data class InvestmentsTexts(
    val quoteUnavailable: String,
    val quoteRestored: String,
    val refreshPaused: String,
    val refreshResumed: String,
)

/**
 * Meus investimentos (US5): the position (`GET /me/investments`) in the API order (FR-019), refreshed every ~10 s
 * while the page is visible and not paused, with the rules of the dashboard (FR-011, FR-012, FR-047): only the
 * availability transitions of the quote and the pause/resume are announced.
 */
class InvestmentsViewModel(
    private val investmentGateway: InvestmentGateway,
    pageVisibility: PageVisibility,
    private val announcer: Announcer,
    private val texts: () -> InvestmentsTexts,
    refreshInterval: Duration = AutoRefresher.DEFAULT_INTERVAL,
    /** Scope of the loads; null uses `viewModelScope`. Tests pass a scope that ends with the test. */
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(InvestmentsUiState())
    private val availability =
        QuoteAvailabilityTracker(
            announcer = announcer,
            unavailableText = { texts().quoteUnavailable },
            restoredText = { texts().quoteRestored },
        )
    private val refresher = AutoRefresher(pageVisibility.isVisible, refreshInterval, ::refreshPosition)

    val uiState: StateFlow<InvestmentsUiState> = mutableState.asStateFlow()

    init {
        refresher.start(this.scope)
    }

    /** "Tentar de novo" of the unavailable quote or of an error: the position is read again at once. */
    fun onRetry() {
        this.scope.launch { refreshPosition() }
    }

    fun onToggleAutoRefresh() {
        val paused = !refresher.isPaused.value
        refresher.setPaused(paused)
        mutableState.update { it.copy(isAutoRefreshPaused = paused) }
        announcer.polite(if (paused) texts().refreshPaused else texts().refreshResumed)
    }

    private suspend fun refreshPosition() {
        val result = investmentGateway.position()
        val position: Loadable<Position> =
            when (result) {
                is Outcome.Success -> {
                    if (result.value.investments.isEmpty()) Loadable.Empty else Loadable.Loaded(result.value)
                }

                is Outcome.Failure -> {
                    Loadable.Failed(result.error)
                }
            }
        mutableState.update { it.copy(position = position) }
        availability.onQuoteState(quoteState(result))
    }

    private fun quoteState(result: Outcome<ApiError, Position>): QuoteState =
        when (result) {
            is Outcome.Success -> QuoteState.Available(result.value.quote)
            is Outcome.Failure -> quoteFailure(result.error)
        }

    private fun quoteFailure(error: ApiError): QuoteState =
        when (error) {
            ApiError.QuoteUnavailable -> QuoteState.Unavailable
            else -> QuoteState.Failed(error)
        }
}
