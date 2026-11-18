package com.kotlinbitcoins.web.state.dashboard

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Profile
import com.kotlinbitcoins.web.core.model.QuoteHistory
import com.kotlinbitcoins.web.core.statement.StatementFilter
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.state.shared.QuoteState
import com.kotlinbitcoins.web.state.support.FakeAnnouncer
import com.kotlinbitcoins.web.state.support.FakeAnnouncer.Announcement
import com.kotlinbitcoins.web.state.support.FakeAnnouncer.Announcement.Polite
import com.kotlinbitcoins.web.state.support.FakeDashboardGateways
import com.kotlinbitcoins.web.state.support.FakePageVisibility
import com.kotlinbitcoins.web.state.support.FakeStatementGateway
import com.kotlinbitcoins.web.state.support.money
import com.kotlinbitcoins.web.state.support.position
import com.kotlinbitcoins.web.state.support.quote
import com.kotlinbitcoins.web.state.support.sampleHistory
import com.kotlinbitcoins.web.state.support.statement
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val gateways = FakeDashboardGateways()
    private val statements = FakeStatementGateway()
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

    private fun TestScope.viewModel(): DashboardViewModel =
        DashboardViewModel(
            profileGateway = gateways,
            accountGateway = gateways,
            quoteGateway = gateways,
            investmentGateway = gateways,
            statementGateway = statements,
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
    fun loadsEveryBlock() =
        runTest {
            val state = viewModel().uiState.value

            assertEquals(Greeting.Named("Maria"), state.greeting)
            assertEquals(Loadable.Loaded(money("1234.56")), state.balance)
            assertEquals(QuoteState.Available(quote()), state.quote)
            assertEquals(PositionTotal.Value(money("704.00")), state.position)
            assertEquals(false, state.isAutoRefreshPaused)
            assertTrue(announcer.announcements.isEmpty())
        }

    @Test
    fun aFailedProfileGreetsWithoutTheNameAndTheOtherBlocksLoad() =
        runTest {
            gateways.profileResult = Outcome.Failure(ApiError.ServiceUnavailable)

            val state = viewModel().uiState.value

            assertEquals(Greeting.Anonymous, state.greeting)
            assertEquals(Loadable.Loaded(money("1234.56")), state.balance)
            assertIs<QuoteState.Available>(state.quote)
            assertEquals(PositionTotal.Value(money("704.00")), state.position)
        }

    @Test
    fun aProfileWithABlankNameGreetsWithoutTheName() =
        runTest {
            gateways.profileResult = Outcome.Success(Profile("   ", "maria@example.com"))
            assertEquals(Greeting.Anonymous, viewModel().uiState.value.greeting)
        }

    @Test
    fun eachBlockFailsAlone() =
        runTest {
            gateways.balanceResult = Outcome.Failure(ApiError.NoConnection)
            gateways.positionResult = Outcome.Failure(ApiError.ServiceUnavailable)

            val state = viewModel().uiState.value

            assertEquals(Greeting.Named("Maria"), state.greeting)
            assertEquals(Loadable.Failed(ApiError.NoConnection), state.balance)
            assertEquals(QuoteState.Available(quote()), state.quote)
            assertEquals(PositionTotal.Failed(ApiError.ServiceUnavailable), state.position)
        }

    @Test
    fun retryingTheBalanceLoadsItAgain() =
        runTest {
            gateways.balanceResult = Outcome.Failure(ApiError.NoConnection)
            val viewModel = viewModel()
            gateways.balanceResult = Outcome.Success(money("10.00"))

            viewModel.onRetryBalance()
            runCurrent()

            assertEquals(Loadable.Loaded(money("10.00")), viewModel.uiState.value.balance)
            assertEquals(2, gateways.balanceCalls)
        }

    @Test
    fun quoteUnavailableShowsNoOldValuesAndThePositionDependsOnTheQuote() =
        runTest {
            val viewModel = viewModel()
            gateways.quoteResult = Outcome.Failure(ApiError.QuoteUnavailable)
            gateways.positionResult = Outcome.Failure(ApiError.QuoteUnavailable)

            advance(REFRESH_MS)

            val state = viewModel.uiState.value
            assertEquals(QuoteState.Unavailable, state.quote)
            assertEquals(PositionTotal.DependsOnQuote, state.position)
            assertEquals(Loadable.Loaded(money("1234.56")), state.balance)
            assertEquals(Greeting.Named("Maria"), state.greeting)
        }

    @Test
    fun whileTheQuoteIsUnavailableEveryCycleTriesAgainUntilItComesBack() =
        runTest {
            gateways.quoteResult = Outcome.Failure(ApiError.QuoteUnavailable)
            gateways.positionResult = Outcome.Failure(ApiError.QuoteUnavailable)
            val viewModel = viewModel()

            advance(REFRESH_MS * 3)
            assertEquals(4, gateways.quoteCalls)
            assertEquals(4, gateways.positionCalls)

            gateways.quoteResult = Outcome.Success(quote(buy = "410000.00"))
            gateways.positionResult = Outcome.Success(position(total = "720.00", investments = 3))
            advance(REFRESH_MS)

            assertEquals(QuoteState.Available(quote(buy = "410000.00")), viewModel.uiState.value.quote)
            assertEquals(PositionTotal.Value(money("720.00")), viewModel.uiState.value.position)
        }

    @Test
    fun withoutInvestmentsTheTotalIsTheZeroOfTheApiWithAnInvitation() =
        runTest {
            gateways.positionResult = Outcome.Success(position(total = "0.00", investments = 0))

            assertEquals(PositionTotal.NoInvestments(money("0.00")), viewModel().uiState.value.position)
        }

    @Test
    fun quoteAndPositionRefreshEveryTenSecondsButProfileAndBalanceDoNot() =
        runTest {
            val viewModel = viewModel()
            gateways.quoteResult = Outcome.Success(quote(buy = "402000.00"))
            gateways.positionResult = Outcome.Success(position(total = "710.00", investments = 3))

            advance(REFRESH_MS - 1)
            assertEquals(1, gateways.quoteCalls)
            advance(1)

            assertEquals(2, gateways.quoteCalls)
            assertEquals(2, gateways.positionCalls)
            assertEquals(1, gateways.profileCalls)
            assertEquals(1, gateways.balanceCalls)
            assertEquals(QuoteState.Available(quote(buy = "402000.00")), viewModel.uiState.value.quote)
            assertEquals(PositionTotal.Value(money("710.00")), viewModel.uiState.value.position)
        }

    @Test
    fun pauseSuspendsTheRefreshAndIsAnnounced() =
        runTest {
            val viewModel = viewModel()

            viewModel.onToggleAutoRefresh()
            runCurrent()
            advance(REFRESH_MS * 3)

            assertEquals(true, viewModel.uiState.value.isAutoRefreshPaused)
            assertEquals(1, gateways.quoteCalls)
            assertEquals(listOf<Announcement>(Polite(TEXTS.refreshPaused)), announcer.announcements)

            viewModel.onToggleAutoRefresh()
            runCurrent()

            assertEquals(false, viewModel.uiState.value.isAutoRefreshPaused)
            assertEquals(2, gateways.quoteCalls)
            assertEquals(
                listOf<Announcement>(Polite(TEXTS.refreshPaused), Polite(TEXTS.refreshResumed)),
                announcer.announcements,
            )
        }

    @Test
    fun theAnnouncedTextsAreReadWhenAnnouncedNotWhenTheViewModelIsCreated() =
        runTest {
            // On the web the string resources load asynchronously: the first composition sees "" (regression).
            texts = DashboardTexts("", "", "", "")
            val viewModel = viewModel()
            texts = TEXTS

            viewModel.onToggleAutoRefresh()

            assertEquals(listOf<Announcement>(Polite(TEXTS.refreshPaused)), announcer.announcements)
        }

    @Test
    fun aHiddenPageDoesNotRefreshAndRefreshesAtOnceWhenVisibleAgain() =
        runTest {
            viewModel()

            visibility.isVisible.value = false
            advance(REFRESH_MS * 3)
            assertEquals(1, gateways.quoteCalls)

            visibility.isVisible.value = true
            runCurrent()
            assertEquals(2, gateways.quoteCalls)
        }

    @Test
    fun onlyTheTransitionsOfTheQuoteAvailabilityAreAnnounced() =
        runTest {
            viewModel()
            gateways.quoteResult = Outcome.Failure(ApiError.QuoteUnavailable)
            advance(REFRESH_MS * 3)
            gateways.quoteResult = Outcome.Success(quote())
            advance(REFRESH_MS * 2)

            assertEquals(
                listOf<Announcement>(Polite(TEXTS.quoteUnavailable), Polite(TEXTS.quoteRestored)),
                announcer.announcements,
            )
        }

    @Test
    fun aRetryOfTheQuoteRefreshesQuoteAndPositionAtOnce() =
        runTest {
            gateways.quoteResult = Outcome.Failure(ApiError.NoConnection)
            val viewModel = viewModel()
            assertEquals(QuoteState.Failed(ApiError.NoConnection), viewModel.uiState.value.quote)
            gateways.quoteResult = Outcome.Success(quote())

            viewModel.onRetryQuote()
            runCurrent()

            assertEquals(QuoteState.Available(quote()), viewModel.uiState.value.quote)
            assertEquals(2, gateways.quoteCalls)
            assertEquals(2, gateways.positionCalls)
        }

    @Test
    fun theInvestmentsFollowThePositionAndGoAwayWithAnUnavailableQuote() =
        runTest {
            val viewModel = viewModel()
            assertEquals(position(total = "704.00", investments = 3).investments, viewModel.uiState.value.investments)

            gateways.positionResult = Outcome.Failure(ApiError.QuoteUnavailable)
            advance(REFRESH_MS)

            assertEquals(PositionTotal.DependsOnQuote, viewModel.uiState.value.position)
            assertTrue(
                viewModel.uiState.value.investments
                    .isEmpty(),
            )
        }

    @Test
    fun theNewestEntriesOfTheDefaultPeriodAreShown() =
        runTest {
            val state = viewModel().uiState.value

            assertEquals(listOf<StatementFilter>(StatementFilter.Default), statements.filters)
            assertEquals(Loadable.Loaded(statement().entries.take(4)), state.recent)
        }

    @Test
    fun aStatementWithoutEntriesIsTheEmptyState() =
        runTest {
            statements.result = Outcome.Success(statement(entries = emptyList()))
            assertEquals(Loadable.Empty, viewModel().uiState.value.recent)
        }

    @Test
    fun theNewestEntriesFailAloneAndCanBeRetried() =
        runTest {
            statements.result = Outcome.Failure(ApiError.NoConnection)
            val viewModel = viewModel()
            assertEquals(Loadable.Failed(ApiError.NoConnection), viewModel.uiState.value.recent)
            assertEquals(Loadable.Loaded(money("1234.56")), viewModel.uiState.value.balance)
            statements.result = Outcome.Success(statement())

            viewModel.onRetryRecent()
            runCurrent()

            assertIs<Loadable.Loaded<*>>(viewModel.uiState.value.recent)
            assertEquals(2, statements.filters.size)
        }

    @Test
    fun thePriceChartLoadsOnceIsEmptyWithoutPointsAndCanBeRetried() =
        runTest {
            gateways.historyResult = Outcome.Success(QuoteHistory(emptyList()))
            val viewModel = viewModel()
            assertEquals(Loadable.Empty, viewModel.uiState.value.history)
            advance(REFRESH_MS)
            assertEquals(1, gateways.historyCalls)
            gateways.historyResult = Outcome.Success(sampleHistory())

            viewModel.onRetryHistory()
            runCurrent()

            assertIs<Loadable.Loaded<*>>(viewModel.uiState.value.history)
            assertEquals(2, gateways.historyCalls)
        }

    @Test
    fun aFailedPriceChartFailsAlone() =
        runTest {
            gateways.historyResult = Outcome.Failure(ApiError.ServiceUnavailable)
            val state = viewModel().uiState.value

            assertEquals(Loadable.Failed(ApiError.ServiceUnavailable), state.history)
            assertIs<QuoteState.Available>(state.quote)
        }

    private companion object {
        const val REFRESH_MS = 10_000L
        val TEXTS =
            DashboardTexts(
                quoteUnavailable = "Cotação indisponível no momento",
                quoteRestored = "Cotação restabelecida",
                refreshPaused = "Atualização automática pausada",
                refreshResumed = "Atualização automática retomada",
            )
    }
}
