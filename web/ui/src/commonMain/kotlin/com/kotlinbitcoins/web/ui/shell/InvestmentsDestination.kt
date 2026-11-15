package com.kotlinbitcoins.web.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.investments.InvestmentsTexts
import com.kotlinbitcoins.web.ui.investments.InvestmentsActions
import com.kotlinbitcoins.web.ui.investments.InvestmentsScreen
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.announce_quote_restored
import com.kotlinbitcoins.web.ui.resources.announce_quote_unavailable
import com.kotlinbitcoins.web.ui.resources.announce_refresh_paused
import com.kotlinbitcoins.web.ui.resources.announce_refresh_resumed
import org.jetbrains.compose.resources.stringResource

/** Meus investimentos (US5) with its ViewModel, which lives as long as the back stack entry. */
@Composable
internal fun InvestmentsDestination(
    viewModels: AppViewModels,
    onNavigate: (AppRoute) -> Unit,
) {
    val texts =
        InvestmentsTexts(
            quoteUnavailable = stringResource(Res.string.announce_quote_unavailable),
            quoteRestored = stringResource(Res.string.announce_quote_restored),
            refreshPaused = stringResource(Res.string.announce_refresh_paused),
            refreshResumed = stringResource(Res.string.announce_refresh_resumed),
        )
    // The resources load asynchronously on the web: the ViewModel reads the latest texts when it announces.
    val currentTexts by rememberUpdatedState(texts)
    val viewModel = viewModel { viewModels.investments { currentTexts } }
    val state by viewModel.uiState.collectAsState()
    InvestmentsScreen(
        state = state,
        actions =
            InvestmentsActions(
                onRetry = viewModel::onRetry,
                onToggleAutoRefresh = viewModel::onToggleAutoRefresh,
                onNavigate = onNavigate,
            ),
    )
}
