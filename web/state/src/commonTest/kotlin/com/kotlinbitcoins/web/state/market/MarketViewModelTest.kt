package com.kotlinbitcoins.web.state.market

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.market.ChartData
import com.kotlinbitcoins.web.core.model.QuoteHistory
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.state.support.FakeMarketGateways
import com.kotlinbitcoins.web.state.support.sampleHistory
import com.kotlinbitcoins.web.state.support.sampleVolume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class MarketViewModelTest {
    private val gateways = FakeMarketGateways()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): MarketViewModel =
        MarketViewModel(gateways, gateways, scope = backgroundScope).also { runCurrent() }

    @Test
    fun volumeAndHistoryLoadWhenThePageOpens() =
        runTest {
            val state = viewModel().uiState.value

            assertEquals(1, gateways.volumeCalls)
            assertEquals(1, gateways.historyCalls)
            assertEquals(Loadable.Loaded(sampleVolume()), state.volume)
            val chart = assertIs<Loadable.Loaded<ChartData>>(state.history).value
            assertEquals(2, chart.buy.size) // the missing slot splits the line
            assertFalse(state.isRefreshing)
        }

    @Test
    fun bothBlocksAreLoadingBeforeTheAnswers() =
        runTest {
            gateways.gate = CompletableDeferred()

            val state = viewModel().uiState.value

            assertEquals(Loadable.Loading, state.volume)
            assertEquals(Loadable.Loading, state.history)
        }

    @Test
    fun theVolumeFailsAlone() =
        runTest {
            gateways.volumeResult = Outcome.Failure(ApiError.NoConnection)

            val state = viewModel().uiState.value

            assertEquals(Loadable.Failed(ApiError.NoConnection), state.volume)
            assertIs<Loadable.Loaded<ChartData>>(state.history)
        }

    @Test
    fun theHistoryFailsAlone() =
        runTest {
            gateways.historyResult = Outcome.Failure(ApiError.ServiceUnavailable)

            val state = viewModel().uiState.value

            assertEquals(Loadable.Loaded(sampleVolume()), state.volume)
            assertEquals(Loadable.Failed(ApiError.ServiceUnavailable), state.history)
        }

    @Test
    fun anEmptyHistoryIsEmptyWithoutTouchingTheVolume() =
        runTest {
            gateways.historyResult = Outcome.Success(QuoteHistory(emptyList()))

            val state = viewModel().uiState.value

            assertEquals(Loadable.Empty, state.history)
            assertEquals(Loadable.Loaded(sampleVolume()), state.volume)
        }

    @Test
    fun refreshReloadsBothKeepingTheValuesWhileInFlight() =
        runTest {
            val viewModel = viewModel()
            val before = viewModel.uiState.value
            gateways.gate = CompletableDeferred()
            gateways.volumeResult = Outcome.Success(sampleVolume(bought = "0.00400000"))

            viewModel.onRefresh()
            runCurrent()

            assertEquals(2, gateways.volumeCalls)
            assertEquals(2, gateways.historyCalls)
            assertTrue(viewModel.uiState.value.isRefreshing)
            assertEquals(before.volume, viewModel.uiState.value.volume)

            gateways.gate?.complete(Unit)
            runCurrent()

            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(Loadable.Loaded(sampleVolume(bought = "0.00400000")), viewModel.uiState.value.volume)
        }

    @Test
    fun aRefreshInFlightIgnoresAnotherOne() =
        runTest {
            val viewModel = viewModel()
            gateways.gate = CompletableDeferred()

            viewModel.onRefresh()
            viewModel.onRefresh()
            runCurrent()

            assertEquals(2, gateways.volumeCalls)
            assertEquals(2, gateways.historyCalls)
        }

    @Test
    fun retryOfOneBlockReloadsOnlyThatBlock() =
        runTest {
            gateways.historyResult = Outcome.Failure(ApiError.NoConnection)
            val viewModel = viewModel()
            gateways.historyResult = Outcome.Success(sampleHistory())

            viewModel.onRetryHistory()
            runCurrent()

            assertEquals(1, gateways.volumeCalls)
            assertEquals(2, gateways.historyCalls)
            assertIs<Loadable.Loaded<ChartData>>(viewModel.uiState.value.history)

            viewModel.onRetryVolume()
            runCurrent()
            assertEquals(2, gateways.volumeCalls)
            assertEquals(2, gateways.historyCalls)
        }

    @Test
    fun thereIsNoAutomaticRefresh() =
        runTest {
            viewModel()

            advanceTimeBy(30.minutes)
            runCurrent()

            assertEquals(1, gateways.volumeCalls)
            assertEquals(1, gateways.historyCalls)
        }
}
