package com.kotlinbitcoins.web.state.statement

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.core.statement.FilterError
import com.kotlinbitcoins.web.core.statement.GroupedStatement
import com.kotlinbitcoins.web.core.statement.StatementFilter
import com.kotlinbitcoins.web.core.statement.StatementItem
import com.kotlinbitcoins.web.core.time.CalendarDate
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.state.support.FakeStatementGateway
import com.kotlinbitcoins.web.state.support.instant
import com.kotlinbitcoins.web.state.support.statement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class StatementViewModelTest {
    private val gateway = FakeStatementGateway()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun date(iso: String): CalendarDate = requireNotNull(CalendarDate.parseIso(iso))

    private val range = StatementFilter.Range(date("2026-10-01"), date("2026-11-09"))

    private fun TestScope.viewModel(route: AppRoute.Statement = AppRoute.Statement()): StatementViewModel =
        StatementViewModel(gateway, route, scope = backgroundScope).also { runCurrent() }

    @Test
    fun defaultRouteLoadsTheDefaultPeriodAndShowsThePeriodOfTheResponse() =
        runTest {
            val state = viewModel().uiState.value

            assertEquals(listOf<StatementFilter>(StatementFilter.Default), gateway.filters)
            assertEquals(StatementFilter.Default, state.filter)
            assertEquals(FilterInput(), state.filterInput)
            assertNull(state.filterError)
            assertEquals(
                StatementPeriod(instant("2026-08-11T00:00:00-03:00"), instant("2026-11-09T09:00:01-03:00")),
                state.period,
            )
            val loaded = assertIs<Loadable.Loaded<GroupedStatement>>(state.statement).value
            assertEquals(3, loaded.items.size)
            val group = assertIs<StatementItem.RedemptionGroup>(loaded.items.first())
            assertEquals("500.01", group.totalRedeemed.toApiString())
        }

    @Test
    fun rangeRouteLoadsTheRangeAndFillsTheFields() =
        runTest {
            val state = viewModel(AppRoute.Statement("2026-10-01", "2026-11-09")).uiState.value

            assertEquals(listOf<StatementFilter>(range), gateway.filters)
            assertEquals(range, state.filter)
            assertEquals(FilterInput("01/10/2026", "09/11/2026"), state.filterInput)
        }

    @Test
    fun applyingAValidFilterNavigatesToTheRouteOfTheFilter() =
        runTest {
            val viewModel = viewModel()
            viewModel.onFromChange("01/10/2026")
            viewModel.onToChange("09/11/2026")

            viewModel.onApplyFilter()
            runCurrent()

            assertEquals(AppRoute.Statement("2026-10-01", "2026-11-09"), viewModel.uiState.value.navigation)
            // The new route has its own page: nothing is read here.
            assertEquals(1, gateway.filters.size)
            viewModel.onNavigationHandled()
            assertNull(viewModel.uiState.value.navigation)
        }

    @Test
    fun applyingTheFilterOfTheCurrentRouteReadsItAgainWithoutNavigating() =
        runTest {
            val viewModel = viewModel(AppRoute.Statement("2026-10-01", "2026-11-09"))

            viewModel.onApplyFilter()
            runCurrent()

            assertNull(viewModel.uiState.value.navigation)
            assertEquals(listOf<StatementFilter>(range, range), gateway.filters)
        }

    @Test
    fun anInvalidFilterShowsTheErrorAndKeepsThePreviousStatement() =
        runTest {
            val viewModel = viewModel()
            val previous = viewModel.uiState.value.statement
            val cases = listOf("10/11/2026" to "01/11/2026", "01/10/2026" to "", "31/02/2026" to "09/11/2026")

            cases.forEachIndexed { index, (from, to) ->
                viewModel.onFromChange(from)
                viewModel.onToChange(to)
                viewModel.onApplyFilter()
                runCurrent()

                val state = viewModel.uiState.value
                assertEquals(FilterError.InvalidRange, state.filterError)
                assertEquals(index + 1, state.filterErrorId)
                assertEquals(previous, state.statement)
                assertNull(state.navigation)
            }
            assertEquals(1, gateway.filters.size)
        }

    @Test
    fun anInvalidRouteLoadsTheDefaultPeriodWithTheErrorAndTheFieldsAsTheyCame() =
        runTest {
            val state = viewModel(AppRoute.Statement("2026-11-10", "2026-11-01")).uiState.value

            assertEquals(FilterError.InvalidRange, state.filterError)
            assertEquals(FilterInput("10/11/2026", "01/11/2026"), state.filterInput)
            assertEquals(listOf<StatementFilter>(StatementFilter.Default), gateway.filters)
            assertIs<Loadable.Loaded<GroupedStatement>>(state.statement)

            val onlyOne = viewModel(AppRoute.Statement(from = "abc")).uiState.value
            assertEquals(FilterInput("abc", ""), onlyOne.filterInput)
            assertEquals(FilterError.InvalidRange, onlyOne.filterError)
        }

    @Test
    fun invalidDateRangeFromTheApiShowsTheErrorAtTheFields() =
        runTest {
            gateway.resultByFilter[range] = Outcome.Failure(ApiError.InvalidDateRange)

            val state = viewModel(AppRoute.Statement("2026-10-01", "2026-11-09")).uiState.value

            assertEquals(FilterError.InvalidRange, state.filterError)
            assertEquals(FilterInput("01/10/2026", "09/11/2026"), state.filterInput)
            // The default period is shown in its place: never a generic error.
            assertEquals(listOf(range, StatementFilter.Default), gateway.filters)
            assertIs<Loadable.Loaded<GroupedStatement>>(state.statement)
        }

    @Test
    fun clearingTheFilterGoesBackToTheDefaultRoute() =
        runTest {
            val viewModel = viewModel(AppRoute.Statement("2026-10-01", "2026-11-09"))

            viewModel.onClearFilter()

            val state = viewModel.uiState.value
            assertEquals(AppRoute.Statement(), state.navigation)
            assertEquals(FilterInput(), state.filterInput)
        }

    @Test
    fun clearingOnTheDefaultRouteReadsItAgainAndClearsTheError() =
        runTest {
            val viewModel = viewModel()
            viewModel.onFromChange("01/10/2026")
            viewModel.onApplyFilter()

            viewModel.onClearFilter()
            runCurrent()

            val state = viewModel.uiState.value
            assertNull(state.navigation)
            assertNull(state.filterError)
            assertEquals(FilterInput(), state.filterInput)
            assertEquals(2, gateway.filters.size)
        }

    @Test
    fun anEmptyStatementIsEmptyWithThePeriod() =
        runTest {
            gateway.result =
                Outcome.Success(
                    statement(
                        from = "2026-10-01T00:00:00-03:00",
                        to = "2026-10-02T00:00:00-03:00",
                        entries = emptyList(),
                    ),
                )

            val state = viewModel().uiState.value

            assertEquals(Loadable.Empty, state.statement)
            assertEquals(
                StatementPeriod(instant("2026-10-01T00:00:00-03:00"), instant("2026-10-02T00:00:00-03:00")),
                state.period,
            )
        }

    @Test
    fun aFailureIsItsOwnStateAndRetryReadsAgain() =
        runTest {
            gateway.result = Outcome.Failure(ApiError.NoConnection)
            val viewModel = viewModel()
            assertEquals(Loadable.Failed(ApiError.NoConnection), viewModel.uiState.value.statement)

            gateway.result = Outcome.Success(statement())
            viewModel.onRetry()
            runCurrent()

            assertIs<Loadable.Loaded<GroupedStatement>>(viewModel.uiState.value.statement)
            assertEquals(2, gateway.filters.size)
        }
}
