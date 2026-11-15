package com.kotlinbitcoins.web.state.investments

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Position
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.state.support.FakeAnnouncer
import com.kotlinbitcoins.web.state.support.FakeAnnouncer.Announcement
import com.kotlinbitcoins.web.state.support.FakeAnnouncer.Announcement.Polite
import com.kotlinbitcoins.web.state.support.FakeDashboardGateways
import com.kotlinbitcoins.web.state.support.FakePageVisibility
import com.kotlinbitcoins.web.state.support.position
import com.kotlinbitcoins.web.state.support.quote
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

@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentsViewModelTest {
    private val gateways = FakeDashboardGateways()
    private val visibility = FakePageVisibility()
    private val announcer = FakeAnnouncer()
    private var texts = TEXTS

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): InvestmentsViewModel =
        InvestmentsViewModel(
            investmentGateway = gateways,
            pageVisibility = visibility,
            announcer = announcer,
            texts = { texts },
            scope = backgroundScope,
        ).also { runCurrent() }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun loadedKeepsTheOrderOfTheApi() =
        runTest {
            val base = position(total = "300.00", investments = 3)
            // The API order is not the order of the ids nor of the dates: the client must not sort (FR-019).
            val (first, second, third) = base.investments
            val unordered = base.copy(investments = listOf(third, first, second))
            gateways.positionResult = Outcome.Success(unordered)

            val state = viewModel().uiState.value

            val loaded = assertIs<Loadable.Loaded<Position>>(state.position)
            assertEquals(listOf("investment-2", "investment-0", "investment-1"), loaded.value.investments.map { it.id })
            assertEquals(unordered, loaded.value)
            assertFalse(state.isQuoteUnavailable)
            assertFalse(state.isAutoRefreshPaused)
            assertTrue(announcer.announcements.isEmpty())
        }

    @Test
    fun anEmptyListIsTheEmptyState() =
        runTest {
            gateways.positionResult = Outcome.Success(position(total = "0.00", investments = 0))

            assertEquals(Loadable.Empty, viewModel().uiState.value.position)
        }

    @Test
    fun quoteUnavailableIsItsOwnStateWithoutOldValuesAndARetry() =
        runTest {
            val viewModel = viewModel()
            assertIs<Loadable.Loaded<Position>>(viewModel.uiState.value.position)
            gateways.positionResult = Outcome.Failure(ApiError.QuoteUnavailable)

            advance(REFRESH_MS)

            assertTrue(viewModel.uiState.value.isQuoteUnavailable)
            assertEquals(Loadable.Failed(ApiError.QuoteUnavailable), viewModel.uiState.value.position)

            gateways.positionResult = Outcome.Success(position(total = "704.00", investments = 3))
            viewModel.onRetry()
            runCurrent()

            assertFalse(viewModel.uiState.value.isQuoteUnavailable)
            assertIs<Loadable.Loaded<Position>>(viewModel.uiState.value.position)
            assertEquals(3, gateways.positionCalls)
        }

    @Test
    fun anotherErrorIsAFailureThatCanBeRetried() =
        runTest {
            gateways.positionResult = Outcome.Failure(ApiError.ServiceUnavailable)
            val viewModel = viewModel()

            assertEquals(Loadable.Failed(ApiError.ServiceUnavailable), viewModel.uiState.value.position)
            assertFalse(viewModel.uiState.value.isQuoteUnavailable)

            gateways.positionResult = Outcome.Success(position(total = "704.00", investments = 3))
            viewModel.onRetry()
            runCurrent()

            assertIs<Loadable.Loaded<Position>>(viewModel.uiState.value.position)
        }

    @Test
    fun thePositionRefreshesEveryTenSeconds() =
        runTest {
            val viewModel = viewModel()
            val refreshed = position(total = "720.00", investments = 3, quote = quote(buy = "450000.00"))
            gateways.positionResult = Outcome.Success(refreshed)

            advance(REFRESH_MS - 1)
            assertEquals(1, gateways.positionCalls)
            advance(1)

            assertEquals(2, gateways.positionCalls)
            assertEquals(Loadable.Loaded(refreshed), viewModel.uiState.value.position)
            assertTrue(announcer.announcements.isEmpty())
        }

    @Test
    fun pauseSuspendsTheRefreshAndIsAnnounced() =
        runTest {
            val viewModel = viewModel()

            viewModel.onToggleAutoRefresh()
            runCurrent()
            advance(REFRESH_MS * 3)

            assertTrue(viewModel.uiState.value.isAutoRefreshPaused)
            assertEquals(1, gateways.positionCalls)

            viewModel.onToggleAutoRefresh()
            runCurrent()

            assertFalse(viewModel.uiState.value.isAutoRefreshPaused)
            assertEquals(2, gateways.positionCalls)
            assertEquals(
                listOf<Announcement>(Polite(TEXTS.refreshPaused), Polite(TEXTS.refreshResumed)),
                announcer.announcements,
            )
        }

    @Test
    fun aHiddenPageDoesNotRefreshAndRefreshesAtOnceWhenVisibleAgain() =
        runTest {
            viewModel()

            visibility.isVisible.value = false
            advance(REFRESH_MS * 3)
            assertEquals(1, gateways.positionCalls)

            visibility.isVisible.value = true
            runCurrent()
            assertEquals(2, gateways.positionCalls)
        }

    @Test
    fun onlyTheTransitionsOfTheQuoteAvailabilityAreAnnounced() =
        runTest {
            viewModel()
            gateways.positionResult = Outcome.Failure(ApiError.QuoteUnavailable)
            advance(REFRESH_MS * 3)
            gateways.positionResult = Outcome.Success(position(total = "704.00", investments = 3))
            advance(REFRESH_MS * 2)

            assertEquals(
                listOf<Announcement>(Polite(TEXTS.quoteUnavailable), Polite(TEXTS.quoteRestored)),
                announcer.announcements,
            )
        }

    @Test
    fun theAnnouncedTextsAreReadWhenAnnouncedNotWhenTheViewModelIsCreated() =
        runTest {
            texts = InvestmentsTexts("", "", "", "")
            val viewModel = viewModel()
            texts = TEXTS

            viewModel.onToggleAutoRefresh()

            assertEquals(listOf<Announcement>(Polite(TEXTS.refreshPaused)), announcer.announcements)
        }

    private companion object {
        const val REFRESH_MS = 10_000L
        val TEXTS =
            InvestmentsTexts(
                quoteUnavailable = "Cotação indisponível no momento",
                quoteRestored = "Cotação restabelecida",
                refreshPaused = "Atualização automática pausada",
                refreshResumed = "Atualização automática retomada",
            )
    }
}
