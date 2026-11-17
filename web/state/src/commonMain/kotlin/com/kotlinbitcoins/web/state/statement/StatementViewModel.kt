package com.kotlinbitcoins.web.state.statement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Statement
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.core.statement.FilterError
import com.kotlinbitcoins.web.core.statement.GroupedStatement
import com.kotlinbitcoins.web.core.statement.StatementFilter
import com.kotlinbitcoins.web.core.time.ApiInstant
import com.kotlinbitcoins.web.core.time.CalendarDate
import com.kotlinbitcoins.web.state.port.StatementGateway
import com.kotlinbitcoins.web.state.shared.Loadable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The two date fields of the filter, as typed (dd/mm/aaaa). */
data class FilterInput(
    val from: String = "",
    val to: String = "",
)

/** The period really queried, as returned by the API (US7-1); shown also in the empty state (US7-7). */
data class StatementPeriod(
    val from: ApiInstant,
    val to: ApiInstant,
)

/**
 * State of the statement (data-model §4.4). [filter] is the one of the route; [statement] keeps the last valid
 * statement when the range is invalid (FR-030). [navigation] is one-shot: the page **replaces** the history entry with
 * it and calls `onNavigationHandled` (contracts/ui-routes.md). [filterErrorId] changes on each refused apply, so the
 * page moves the focus to the date fields even when the error was already shown.
 */
data class StatementUiState(
    val filter: StatementFilter = StatementFilter.Default,
    val filterInput: FilterInput = FilterInput(),
    val filterError: FilterError? = null,
    val filterErrorId: Int = 0,
    val period: StatementPeriod? = null,
    val statement: Loadable<GroupedStatement> = Loadable.Loading,
    val navigation: AppRoute.Statement? = null,
)

/**
 * Extrato (US7): loads the period of the route (default or `de`/`ate`); a valid filter becomes a new route, so it
 * survives reload and login (FR-027); an invalid one — locally or INVALID_DATE_RANGE from the API — shows the error
 * at the date fields and keeps the previous statement (FR-030). An invalid route loads the default period with the
 * error and the fields filled with what came (contracts/ui-routes.md, rule 3).
 */
class StatementViewModel(
    private val statementGateway: StatementGateway,
    private val route: AppRoute.Statement,
    /** Scope of the loads; null uses `viewModelScope`. Tests pass a scope that ends with the test. */
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(initialState(route))
    private var loading: Job? = null

    val uiState: StateFlow<StatementUiState> = mutableState.asStateFlow()

    init {
        load(mutableState.value.filter)
    }

    fun onFromChange(text: String) = mutableState.update { it.copy(filterInput = it.filterInput.copy(from = text)) }

    fun onToChange(text: String) = mutableState.update { it.copy(filterInput = it.filterInput.copy(to = text)) }

    fun onApplyFilter() {
        val input = mutableState.value.filterInput
        when (val parsed = StatementFilter.fromUserInput(input.from, input.to)) {
            is Outcome.Success -> goTo(parsed.value)
            is Outcome.Failure -> showFilterError(parsed.error)
        }
    }

    /** Back to the default period of the API. */
    fun onClearFilter() {
        mutableState.update { it.copy(filterInput = FilterInput()) }
        goTo(StatementFilter.Default)
    }

    fun onRetry() = load(mutableState.value.filter)

    fun onNavigationHandled() = mutableState.update { it.copy(navigation = null) }

    private fun goTo(filter: StatementFilter) {
        val target = AppRoute.Statement.of(filter)
        if (target == route) {
            // Same route (e.g. the filter already applied): the history does not change, the statement is read again.
            mutableState.update { it.copy(filterError = null) }
            load(filter)
        } else {
            mutableState.update { it.copy(navigation = target) }
        }
    }

    private fun showFilterError(error: FilterError) =
        mutableState.update { it.copy(filterError = error, filterErrorId = it.filterErrorId + 1) }

    private fun load(filter: StatementFilter) {
        loading?.cancel()
        mutableState.update { it.copy(filter = filter, statement = Loadable.Loading) }
        loading = scope.launch { onResult(filter, statementGateway.statement(filter)) }
    }

    private fun onResult(
        filter: StatementFilter,
        result: Outcome<ApiError, Statement>,
    ) {
        when (result) {
            is Outcome.Success -> {
                val grouped = GroupedStatement.of(result.value)
                mutableState.update {
                    it.copy(
                        period = StatementPeriod(grouped.from, grouped.to),
                        statement = if (grouped.items.isEmpty()) Loadable.Empty else Loadable.Loaded(grouped),
                    )
                }
            }

            is Outcome.Failure -> {
                when (val error = result.error) {
                    ApiError.InvalidDateRange -> onRangeRefused(filter)

                    is ApiError.Validation,
                    ApiError.InvalidAmount,
                    ApiError.EmailAlreadyInUse,
                    ApiError.InvalidCredentials,
                    ApiError.Unauthorized,
                    ApiError.InsufficientBalance,
                    ApiError.RedemptionExceedsAvailable,
                    ApiError.QuoteUnavailable,
                    ApiError.ServiceUnavailable,
                    ApiError.Unexpected,
                    ApiError.NoConnection,
                    -> onFailure(error)
                }
            }
        }
    }

    /**
     * INVALID_DATE_RANGE: the error at the fields. A range only reaches the API as the filter of the route, when
     * nothing is shown yet, so the default period is loaded in its place (as for an invalid route).
     */
    private fun onRangeRefused(filter: StatementFilter) {
        mutableState.update { it.copy(filterError = FilterError.InvalidRange, filterErrorId = it.filterErrorId + 1) }
        if (filter != StatementFilter.Default) load(StatementFilter.Default)
    }

    private fun onFailure(error: ApiError) = mutableState.update { it.copy(statement = Loadable.Failed(error)) }

    private companion object {
        fun initialState(route: AppRoute.Statement): StatementUiState =
            when (val parsed = route.toFilter()) {
                is Outcome.Success -> {
                    StatementUiState(filter = parsed.value, filterInput = inputOf(parsed.value))
                }

                is Outcome.Failure -> {
                    StatementUiState(
                        filterInput = FilterInput(userText(route.from), userText(route.to)),
                        filterError = parsed.error,
                        filterErrorId = 1,
                    )
                }
            }

        fun inputOf(filter: StatementFilter): FilterInput =
            when (filter) {
                StatementFilter.Default -> FilterInput()
                is StatementFilter.Range -> FilterInput(filter.from.toUserString(), filter.to.toUserString())
            }

        /** What came in the route: a valid ISO date in the field format, anything else as it is. */
        fun userText(raw: String?): String = raw?.let { CalendarDate.parseIso(it)?.toUserString() ?: it }.orEmpty()
    }
}
