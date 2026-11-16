package com.kotlinbitcoins.web.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kotlinbitcoins.web.ui.market.MarketActions
import com.kotlinbitcoins.web.ui.market.MarketScreen

/** Mercado (US8) with its ViewModel; whether the data table is open is a detail of the page, kept here. */
@Composable
internal fun MarketDestination(viewModels: AppViewModels) {
    val viewModel = viewModel { viewModels.market() }
    val state by viewModel.uiState.collectAsState()
    var isTableVisible by rememberSaveable { mutableStateOf(false) }
    MarketScreen(
        state = state,
        isTableVisible = isTableVisible,
        actions =
            MarketActions(
                onRefresh = viewModel::onRefresh,
                onRetryVolume = viewModel::onRetryVolume,
                onRetryHistory = viewModel::onRetryHistory,
                onToggleTable = { isTableVisible = !isTableVisible },
            ),
    )
}
