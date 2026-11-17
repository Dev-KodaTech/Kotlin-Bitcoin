package com.kotlinbitcoins.web.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.ui.statement.StatementActions
import com.kotlinbitcoins.web.ui.statement.StatementScreen

/**
 * Extrato (US7) with its ViewModel, created with the route of the entry (its filter). Applying or clearing the filter
 * **replaces** the history entry with the new route (contracts/ui-routes.md), so the filter survives reload and
 * back/forward without filling the history; the new entry has its own ViewModel. A filter that arrives by the URL
 * while the statement is on screen (address bar, `#/extrato?…` link) reuses the entry (`launchSingleTop`), so the
 * ViewModel is keyed by the route: another period is another ViewModel, which loads it.
 */
@Composable
internal fun StatementDestination(
    viewModels: AppViewModels,
    route: AppRoute.Statement,
    onReplace: (AppRoute) -> Unit,
) {
    val viewModel = viewModel(key = "statement:${route.from}:${route.to}") { viewModels.statement(route) }
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.navigation) {
        val target = state.navigation ?: return@LaunchedEffect
        viewModel.onNavigationHandled()
        onReplace(target)
    }
    StatementScreen(
        state = state,
        actions =
            StatementActions(
                onFromChange = viewModel::onFromChange,
                onToChange = viewModel::onToChange,
                onApplyFilter = viewModel::onApplyFilter,
                onClearFilter = viewModel::onClearFilter,
                onRetry = viewModel::onRetry,
            ),
    )
}
