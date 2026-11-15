package com.kotlinbitcoins.web.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.deposit.DepositTexts
import com.kotlinbitcoins.web.state.shared.OperationState
import com.kotlinbitcoins.web.ui.deposit.DepositActions
import com.kotlinbitcoins.web.ui.deposit.DepositScreen
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.announce_deposit_succeeded
import com.kotlinbitcoins.web.ui.resources.page_deposit
import com.kotlinbitcoins.web.ui.resources.page_deposit_succeeded
import com.kotlinbitcoins.web.ui.resources.page_title_format
import org.jetbrains.compose.resources.stringResource

/**
 * Deposit (US3) with its ViewModel, which lives as long as the back stack entry: a reload or a new visit starts from
 * an empty field (FR-043). The result step has no route of its own, so it changes `document.title` to
 * "Depósito realizado · …" (contracts/ui-routes.md) and back when a new deposit starts.
 */
@Composable
internal fun DepositDestination(
    viewModels: AppViewModels,
    ports: ShellPorts,
    onNavigate: (AppRoute) -> Unit,
) {
    val texts = DepositTexts(succeeded = stringResource(Res.string.announce_deposit_succeeded))
    // The resources load asynchronously on the web: the ViewModel reads the latest texts when it announces.
    val currentTexts by rememberUpdatedState(texts)
    val viewModel = viewModel { viewModels.deposit { currentTexts } }
    val state by viewModel.uiState.collectAsState()

    val succeeded = state.operation is OperationState.Succeeded
    val title =
        stringResource(
            Res.string.page_title_format,
            stringResource(if (succeeded) Res.string.page_deposit_succeeded else Res.string.page_deposit),
        )
    LaunchedEffect(title) { ports.documentTitle.set(title) }

    DepositScreen(
        state = state,
        actions =
            DepositActions(
                onAmountChange = viewModel::onAmountChange,
                onConfirm = viewModel::onConfirm,
                onRetry = viewModel::onRetry,
                onEdit = viewModel::onEdit,
                onNewOperation = viewModel::onNewOperation,
                onNavigate = onNavigate,
                announce = ports.announcer::assertive,
            ),
    )
}
