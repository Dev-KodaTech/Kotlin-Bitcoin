package com.kotlinbitcoins.web.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.purchase.PurchaseTexts
import com.kotlinbitcoins.web.state.shared.OperationState
import com.kotlinbitcoins.web.ui.purchase.PurchaseActions
import com.kotlinbitcoins.web.ui.purchase.PurchasePageActions
import com.kotlinbitcoins.web.ui.purchase.PurchaseScreen
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.announce_purchase_succeeded
import com.kotlinbitcoins.web.ui.resources.announce_refresh_paused
import com.kotlinbitcoins.web.ui.resources.announce_refresh_resumed
import com.kotlinbitcoins.web.ui.resources.page_purchase
import com.kotlinbitcoins.web.ui.resources.page_purchase_review
import com.kotlinbitcoins.web.ui.resources.page_purchase_succeeded
import com.kotlinbitcoins.web.ui.resources.page_title_format
import org.jetbrains.compose.resources.stringResource

/**
 * Purchase (US4) with its ViewModel, which lives as long as the back stack entry: a reload or a new visit starts from
 * an empty field (FR-043). The review and result steps have no route of their own, so they change `document.title` to
 * "Revisar compra · …" and "Compra realizada · …" (contracts/ui-routes.md) and back when the person edits again.
 */
@Composable
internal fun PurchaseDestination(
    viewModels: AppViewModels,
    ports: ShellPorts,
    onNavigate: (AppRoute) -> Unit,
) {
    val texts =
        PurchaseTexts(
            succeeded = stringResource(Res.string.announce_purchase_succeeded),
            refreshPaused = stringResource(Res.string.announce_refresh_paused),
            refreshResumed = stringResource(Res.string.announce_refresh_resumed),
        )
    // The resources load asynchronously on the web: the ViewModel reads the latest texts when it announces.
    val currentTexts by rememberUpdatedState(texts)
    val viewModel = viewModel { viewModels.purchase { currentTexts } }
    val state by viewModel.uiState.collectAsState()

    val page =
        when (state.operation) {
            is OperationState.Reviewing, is OperationState.Submitting -> Res.string.page_purchase_review
            is OperationState.Succeeded -> Res.string.page_purchase_succeeded
            else -> Res.string.page_purchase
        }
    val title = stringResource(Res.string.page_title_format, stringResource(page))
    LaunchedEffect(title) { ports.documentTitle.set(title) }

    PurchaseScreen(
        state = state,
        actions =
            PurchaseActions(
                onAmountChange = viewModel::onAmountChange,
                onReview = viewModel::onReview,
                onConfirm = viewModel::onConfirm,
                onEdit = viewModel::onEdit,
                onRetry = viewModel::onRetry,
                onNewOperation = viewModel::onNewOperation,
                page =
                    PurchasePageActions(
                        onRetryBalance = viewModel::onRetryBalance,
                        onToggleAutoRefresh = viewModel::onToggleAutoRefresh,
                        onNavigate = onNavigate,
                        announce = ports.announcer::assertive,
                    ),
            ),
    )
}
