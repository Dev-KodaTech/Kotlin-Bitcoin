package com.kotlinbitcoins.web.ui.redemption

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.estimate.RedeemEstimate
import com.kotlinbitcoins.web.core.model.Position
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.redemption.RedemptionUiState
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.state.shared.OperationInputError
import com.kotlinbitcoins.web.state.shared.OperationState
import com.kotlinbitcoins.web.ui.components.AmountField
import com.kotlinbitcoins.web.ui.components.AppCard
import com.kotlinbitcoins.web.ui.components.CardPadding
import com.kotlinbitcoins.web.ui.components.ErrorBlock
import com.kotlinbitcoins.web.ui.components.EstimateCard
import com.kotlinbitcoins.web.ui.components.EstimateDisplay
import com.kotlinbitcoins.web.ui.components.FocusOnArrival
import com.kotlinbitcoins.web.ui.components.LoadingIndicator
import com.kotlinbitcoins.web.ui.components.MinTouchTarget
import com.kotlinbitcoins.web.ui.components.PageBottomPadding
import com.kotlinbitcoins.web.ui.components.PageHeading
import com.kotlinbitcoins.web.ui.components.PauseToggle
import com.kotlinbitcoins.web.ui.components.PrimaryButton
import com.kotlinbitcoins.web.ui.components.SecondaryButton
import com.kotlinbitcoins.web.ui.components.SectionCard
import com.kotlinbitcoins.web.ui.components.SubmitButton
import com.kotlinbitcoins.web.ui.components.UncertainOutcomeBlock
import com.kotlinbitcoins.web.ui.components.focusOutline
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.brasilia_time
import com.kotlinbitcoins.web.ui.resources.error_available_now
import com.kotlinbitcoins.web.ui.resources.error_invalid_amount
import com.kotlinbitcoins.web.ui.resources.estimate_buy_quote
import com.kotlinbitcoins.web.ui.resources.page_redemption
import com.kotlinbitcoins.web.ui.resources.quote_at
import com.kotlinbitcoins.web.ui.resources.quote_unavailable
import com.kotlinbitcoins.web.ui.resources.redemption_amount
import com.kotlinbitcoins.web.ui.resources.redemption_available
import com.kotlinbitcoins.web.ui.resources.redemption_available_notice
import com.kotlinbitcoins.web.ui.resources.redemption_available_quote
import com.kotlinbitcoins.web.ui.resources.redemption_buy
import com.kotlinbitcoins.web.ui.resources.redemption_confirm
import com.kotlinbitcoins.web.ui.resources.redemption_edit
import com.kotlinbitcoins.web.ui.resources.redemption_empty
import com.kotlinbitcoins.web.ui.resources.redemption_estimate_notice
import com.kotlinbitcoins.web.ui.resources.redemption_new
import com.kotlinbitcoins.web.ui.resources.redemption_redeem_all
import com.kotlinbitcoins.web.ui.resources.redemption_redeem_all_warning
import com.kotlinbitcoins.web.ui.resources.redemption_review
import com.kotlinbitcoins.web.ui.shared.message
import com.kotlinbitcoins.web.ui.theme.LocalExtendedColors
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.stringResource

/**
 * Redemption (US6): h1 "Resgatar", the available with its buy quote and instant (FR-022) and the pause of its refresh,
 * and three steps without routes of their own (W-04). Editing: the amount in R$, "Resgatar tudo" (fills the field
 * only) and "Revisar resgate". Review: the labeled estimate by the buy quote (FR-023), with the warning of "Resgatar
 * tudo", and "Confirmar resgate", sent once (FR-043). After sending: only the result of the API (FR-024), a definitive
 * error (FR-025: the available read again and "Resgatar tudo" when above the available, "Tentar de novo" when the
 * quote or the service was unavailable) or the uncertain result without resend (FR-041). Stateless.
 */
@Composable
fun RedemptionScreen(
    state: RedemptionUiState,
    actions: RedemptionActions,
) {
    // The form shown again after a review or a result takes the focus on its field ("Alterar valor", "Novo resgate").
    var leftEditing by remember { mutableStateOf(false) }
    val operation = state.operation
    SideEffect { if (operation !is OperationState.Editing) leftEditing = true }
    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .widthIn(max = MaxContentWidth)
                .padding(bottom = PageBottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeading(stringResource(Res.string.page_redemption))

        val showsAvailable = operation !is OperationState.Succeeded && operation != OperationState.Uncertain
        if (showsAvailable) {
            PauseToggle(isPaused = state.isAutoRefreshPaused, onToggle = actions.page.onToggleAutoRefresh)
            AvailableBlock(state, actions.page)
        }

        // One call site for reviewing and sending, so the review keeps its identity (and focus) between them.
        val review =
            when (operation) {
                is OperationState.Reviewing -> ReviewModel(operation.amount, isSubmitting = false)
                is OperationState.Submitting -> ReviewModel(operation.amount, isSubmitting = true)
                else -> null
            }
        if (review != null) RedemptionReview(review, state, actions)

        when (operation) {
            is OperationState.Editing -> {
                RedemptionForm(operation, state, focusOnShow = leftEditing, actions = actions)
            }

            is OperationState.Reviewing, is OperationState.Submitting -> {}

            is OperationState.Succeeded -> {
                RedemptionResultContent(operation.result)
                PrimaryButton(
                    onClick = actions.onNewOperation,
                    modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline(),
                ) {
                    Text(stringResource(Res.string.redemption_new))
                }
            }

            is OperationState.Rejected -> {
                RedemptionRejected(operation.error, state, actions)
            }

            OperationState.Uncertain -> {
                UncertainOutcomeBlock(
                    onViewBalance = { actions.page.onNavigate(AppRoute.Dashboard) },
                    onViewStatement = { actions.page.onNavigate(AppRoute.Statement()) },
                    announce = actions.page.announce,
                )
            }
        }
    }
}

/** The available (US6-1): total current value, the buy quote and its instant, marked as a changing estimate. */
@Composable
private fun AvailableBlock(
    state: RedemptionUiState,
    page: RedemptionPageActions,
) {
    AppCard(modifier = Modifier.fillMaxWidth(), color = LocalExtendedColors.current.tint) {
        Column(modifier = Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (val available = state.available) {
                Loadable.Loading, Loadable.Empty -> {
                    LoadingIndicator()
                }

                is Loadable.Failed -> {
                    val message =
                        if (available.error == ApiError.QuoteUnavailable) {
                            stringResource(Res.string.quote_unavailable)
                        } else {
                            available.error.message()
                        }
                    ErrorBlock(message, onRetry = page.onRetryAvailable)
                }

                is Loadable.Loaded -> {
                    AvailableValues(available.value)
                }
            }
            // US6-7: R$ 0,00 with an invitation to buy; sending stays allowed (the API answers).
            if (state.isEmpty) BuyInvitation(onBuy = { page.onNavigate(AppRoute.Purchase) })
        }
    }
}

/** The invitation of the empty state, inside the block of the available: the text and, beside it, the shortcut. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuyInvitation(onBuy: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.redemption_empty), style = MaterialTheme.typography.bodyLarge)
        SecondaryButton(onClick = onBuy, modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline()) {
            Text(stringResource(Res.string.redemption_buy))
        }
    }
}

@Composable
private fun AvailableValues(position: Position) {
    Text(
        text = stringResource(Res.string.redemption_available, position.totalCurrentValue.format()),
        style = MaterialTheme.typography.titleLarge.numeric(),
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = stringResource(Res.string.redemption_available_quote, position.quote.buyPrice.format()),
        style = MaterialTheme.typography.bodyLarge.numeric(),
    )
    Text(
        text =
            stringResource(
                Res.string.quote_at,
                position.quote.quotedAt.formatWithSeconds(),
                stringResource(Res.string.brasilia_time),
            ),
        style = MaterialTheme.typography.bodySmall.numeric(),
    )
    Text(stringResource(Res.string.redemption_available_notice), style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RedemptionForm(
    editing: OperationState.Editing,
    state: RedemptionUiState,
    focusOnShow: Boolean,
    actions: RedemptionActions,
) {
    val fieldFocus = remember { FocusRequester() }
    // Focus on the field after every refused review or refusal of the API, even when the error repeats (FR-046).
    LaunchedEffect(state.attempt, editing.inputError) { if (editing.inputError != null) fieldFocus.requestFocus() }
    LaunchedEffect(Unit) { if (focusOnShow) fieldFocus.requestFocus() }

    SectionCard {
        AmountField(
            value = editing.input,
            onValueChange = actions.onAmountChange,
            label = stringResource(Res.string.redemption_amount),
            error = editing.inputError?.let { inputErrorMessage(it) },
            focusRequester = fieldFocus,
            onDone = actions.onReview,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SubmitButton(
                text = stringResource(Res.string.redemption_review),
                isSubmitting = false,
                onClick = actions.onReview,
            )
            RedeemAllButton(enabled = state.canRedeemAll, onClick = actions.page.onRedeemAll)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RedemptionReview(
    review: ReviewModel,
    state: RedemptionUiState,
    actions: RedemptionActions,
) {
    FocusOnArrival(enabled = true) { arrivalModifier ->
        Text(
            text = stringResource(Res.string.redemption_review),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }.then(arrivalModifier),
        )
    }
    ResultLine(stringResource(Res.string.redemption_amount), review.amount.format())
    EstimateCard(
        display = estimateDisplay(state.estimate, state.available),
        priceLabel = stringResource(Res.string.estimate_buy_quote),
        notice = stringResource(Res.string.redemption_estimate_notice),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The decision is the API's: the confirmation stays enabled without a quote (FR-025).
        SubmitButton(
            text = stringResource(Res.string.redemption_confirm),
            isSubmitting = review.isSubmitting,
            onClick = actions.onConfirm,
        )
        if (!review.isSubmitting) {
            SecondaryButton(
                onClick = actions.onEdit,
                modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline(),
            ) {
                Text(stringResource(Res.string.redemption_edit))
            }
        }
    }
}

@Composable
private fun estimateDisplay(
    estimate: RedeemEstimate?,
    available: Loadable<Position>,
): EstimateDisplay {
    if (estimate != null) {
        val warnings =
            if (estimate.fromRedeemAll) {
                listOf(
                    stringResource(Res.string.redemption_redeem_all_warning),
                )
            } else {
                emptyList()
            }
        return EstimateDisplay.Ready(estimate.btc, estimate.quote.buyPrice, estimate.quote.quotedAt, warnings)
    }
    return when (available) {
        is Loadable.Failed -> {
            if (available.error == ApiError.QuoteUnavailable) {
                EstimateDisplay.QuoteUnavailable
            } else {
                EstimateDisplay.Failed(available.error)
            }
        }

        Loadable.Loading, Loadable.Empty, is Loadable.Loaded -> {
            EstimateDisplay.Loading
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RedemptionRejected(
    error: ApiError,
    state: RedemptionUiState,
    actions: RedemptionActions,
) {
    val retryable = error == ApiError.QuoteUnavailable || error == ApiError.ServiceUnavailable
    ErrorBlock(
        message = error.message(),
        onRetry = if (retryable) actions.onRetry else null,
        focusOnShow = true,
    )
    val exceeds = error == ApiError.RedemptionExceedsAvailable
    if (exceeds) {
        // The available was read again after the refusal (US6-6).
        (state.available as? Loadable.Loaded)?.let { loaded ->
            Text(
                text = stringResource(Res.string.error_available_now, loaded.value.totalCurrentValue.format()),
                style = MaterialTheme.typography.bodyLarge.numeric(),
            )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (exceeds) RedeemAllButton(enabled = state.canRedeemAll, onClick = actions.page.onRedeemAll)
        SecondaryButton(
            onClick = actions.onEdit,
            modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline(),
        ) {
            Text(stringResource(Res.string.redemption_edit))
        }
    }
}

@Composable
private fun RedeemAllButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SecondaryButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline(),
    ) {
        Text(stringResource(Res.string.redemption_redeem_all))
    }
}

@Composable
private fun inputErrorMessage(error: OperationInputError): String =
    when (error) {
        is OperationInputError.Invalid -> stringResource(Res.string.error_invalid_amount)
        OperationInputError.RefusedByApi -> ApiError.InvalidAmount.message()
    }

/** What the review shows while reviewing or sending. */
private data class ReviewModel(
    val amount: Money,
    val isSubmitting: Boolean,
)

private val MaxContentWidth = 640.dp
