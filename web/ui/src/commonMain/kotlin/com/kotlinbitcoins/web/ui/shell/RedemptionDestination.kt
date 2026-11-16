package com.kotlinbitcoins.web.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.redemption.RedemptionTexts
import com.kotlinbitcoins.web.state.shared.OperationState
import com.kotlinbitcoins.web.ui.redemption.RedemptionActions
import com.kotlinbitcoins.web.ui.redemption.RedemptionPageActions
import com.kotlinbitcoins.web.ui.redemption.RedemptionScreen
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.announce_redemption_succeeded
import com.kotlinbitcoins.web.ui.resources.announce_refresh_paused
import com.kotlinbitcoins.web.ui.resources.announce_refresh_resumed
import com.kotlinbitcoins.web.ui.resources.page_redemption
import com.kotlinbitcoins.web.ui.resources.page_redemption_review
import com.kotlinbitcoins.web.ui.resources.page_redemption_succeeded
import com.kotlinbitcoins.web.ui.resources.page_title_format
import org.jetbrains.compose.resources.stringResource

/**
 * Redemption (US6) with its ViewModel, which lives as long as the back stack entry: a reload or a new visit starts
 * from an empty field (FR-043). The review and result steps have no route of their own, so they change
 * `document.title` to "Revisar resgate · …" and "Resgate realizado · …" (contracts/ui-routes.md).
 */
@Composable
internal fun RedemptionDestination(
    viewModels: AppViewModels,
    ports: ShellPorts,
    onNavigate: (AppRoute) -> Unit,
) {
    val texts =
        RedemptionTexts(
            succeeded = stringResource(Res.string.announce_redemption_succeeded),
            refreshPaused = stringResource(Res.string.announce_refresh_paused),
            refreshResumed = stringResource(Res.string.announce_refresh_resumed),
        )
    // The resources load asynchronously on the web: the ViewModel reads the latest texts when it announces.
    val currentTexts by rememberUpdatedState(texts)
    val viewModel = viewModel { viewModels.redemption { currentTexts } }
    val state by viewModel.uiState.collectAsState()

    val page =
        when (state.operation) {
            is OperationState.Reviewing, is OperationState.Submitting -> Res.string.page_redemption_review
            is OperationState.Succeeded -> Res.string.page_redemption_succeeded
            else -> Res.string.page_redemption
        }
    val title = stringResource(Res.string.page_title_format, stringResource(page))
    LaunchedEffect(title) { ports.documentTitle.set(title) }

    RedemptionScreen(
        state = state,
        actions =
            RedemptionActions(
                onAmountChange = viewModel::onAmountChange,
                onReview = viewModel::onReview,
                onConfirm = viewModel::onConfirm,
                onEdit = viewModel::onEdit,
                onRetry = viewModel::onRetry,
                onNewOperation = viewModel::onNewOperation,
                page =
                    RedemptionPageActions(
                        onRedeemAll = viewModel::onRedeemAll,
                        onRetryAvailable = viewModel::onRetryAvailable,
                        onToggleAutoRefresh = viewModel::onToggleAutoRefresh,
                        onNavigate = onNavigate,
                        announce = ports.announcer::assertive,
                    ),
            ),
    )
}
