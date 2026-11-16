package com.kotlinbitcoins.web.state.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.market.ChartData
import com.kotlinbitcoins.web.core.market.ChartSeries
import com.kotlinbitcoins.web.core.model.DailyVolume
import com.kotlinbitcoins.web.core.model.QuoteHistory
import com.kotlinbitcoins.web.state.port.MarketGateway
import com.kotlinbitcoins.web.state.port.QuoteGateway
import com.kotlinbitcoins.web.state.shared.Loadable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Mercado (data-model §4.4): two independent blocks (FR-013). [history] is [Loadable.Empty] without
 * points (US8-5). [isRefreshing] is true while "Atualizar" reloads both; the values shown stay until the answers.
 */
data class MarketUiState(
    val volume: Loadable<DailyVolume> = Loadable.Loading,
    val history: Loadable<ChartData> = Loadable.Loading,
    val isRefreshing: Boolean = false,
)

/**
 * Mercado (US8): the volume of the day (`GET /market/daily-volume`) and the 24 h chart (`GET /quotes/history`) load
 * when the page opens and again only by "Atualizar" (FR-035); there is no automatic refresh. Each block fails alone.
 */
class MarketViewModel(
    private val marketGateway: MarketGateway,
    private val quoteGateway: QuoteGateway,
    /** Scope of the loads; null uses `viewModelScope`. Tests pass a scope that ends with the test. */
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(MarketUiState())

    val uiState: StateFlow<MarketUiState> = mutableState.asStateFlow()

    init {
        reloadBoth()
    }

    /** "Atualizar": both blocks again; ignored while a refresh is in flight. */
    fun onRefresh() {
        if (mutableState.value.isRefreshing) return
        mutableState.update { it.copy(isRefreshing = true) }
        reloadBoth()
    }

    /** "Tentar de novo" of the volume block. */
    fun onRetryVolume() {
        mutableState.update { it.copy(volume = Loadable.Loading) }
        scope.launch { onVolume(marketGateway.dailyVolume()) }
    }

    /** "Tentar de novo" of the chart block. */
    fun onRetryHistory() {
        mutableState.update { it.copy(history = Loadable.Loading) }
        scope.launch { onHistory(quoteGateway.history()) }
    }

    private fun reloadBoth() {
        scope.launch {
            val volume = async { marketGateway.dailyVolume() }
            val history = async { quoteGateway.history() }
            onVolume(volume.await())
            onHistory(history.await())
            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun onVolume(result: Outcome<ApiError, DailyVolume>) =
        mutableState.update { it.copy(volume = loadable(result) { volume -> Loadable.Loaded(volume) }) }

    private fun onHistory(result: Outcome<ApiError, QuoteHistory>) =
        mutableState.update {
            it.copy(
                history =
                    loadable(result) { history ->
                        ChartSeries.chartData(history)?.let { chart -> Loadable.Loaded(chart) } ?: Loadable.Empty
                    },
            )
        }

    private inline fun <T, R> loadable(
        result: Outcome<ApiError, T>,
        onSuccess: (T) -> Loadable<R>,
    ): Loadable<R> =
        when (result) {
            is Outcome.Success -> onSuccess(result.value)
            is Outcome.Failure -> Loadable.Failed(result.error)
        }
}
