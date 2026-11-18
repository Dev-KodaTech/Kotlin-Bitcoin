package com.kotlinbitcoins.web.ui.purchase

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.estimate.BuyEstimate
import com.kotlinbitcoins.web.core.model.PurchaseResult
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.purchase.PurchaseUiState
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.state.shared.OperationInputError
import com.kotlinbitcoins.web.state.shared.OperationState
import com.kotlinbitcoins.web.state.shared.QuoteState
import com.kotlinbitcoins.web.ui.components.AmountField
import com.kotlinbitcoins.web.ui.components.AppCard
import com.kotlinbitcoins.web.ui.components.CardPadding
import com.kotlinbitcoins.web.ui.components.ErrorBlock
import com.kotlinbitcoins.web.ui.components.EstimateCard
import com.kotlinbitcoins.web.ui.components.EstimateDisplay
import com.kotlinbitcoins.web.ui.components.FocusOnArrival
import com.kotlinbitcoins.web.ui.components.LoadableBlock
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
import com.kotlinbitcoins.web.ui.resources.error_available_balance
import com.kotlinbitcoins.web.ui.resources.error_invalid_amount
import com.kotlinbitcoins.web.ui.resources.estimate_sell_quote
import com.kotlinbitcoins.web.ui.resources.page_purchase
import com.kotlinbitcoins.web.ui.resources.purchase_amount
import com.kotlinbitcoins.web.ui.resources.purchase_available_balance
import com.kotlinbitcoins.web.ui.resources.purchase_below_one_satoshi
import com.kotlinbitcoins.web.ui.resources.purchase_btc
import com.kotlinbitcoins.web.ui.resources.purchase_confirm
import com.kotlinbitcoins.web.ui.resources.purchase_deposit
import com.kotlinbitcoins.web.ui.resources.purchase_edit
import com.kotlinbitcoins.web.ui.resources.purchase_estimate_notice
import com.kotlinbitcoins.web.ui.resources.purchase_invested
import com.kotlinbitcoins.web.ui.resources.purchase_new
import com.kotlinbitcoins.web.ui.resources.purchase_new_balance
import com.kotlinbitcoins.web.ui.resources.purchase_purchased_at
import com.kotlinbitcoins.web.ui.resources.purchase_quote_used
import com.kotlinbitcoins.web.ui.resources.purchase_result_title
import com.kotlinbitcoins.web.ui.resources.purchase_review
import com.kotlinbitcoins.web.ui.shared.ErrorContext
import com.kotlinbitcoins.web.ui.shared.message
import com.kotlinbitcoins.web.ui.theme.LocalExtendedColors
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.stringResource

/**
 * Purchase (US4): h1 "Comprar bitcoin" and three steps without routes of their own (W-04). Editing: the amount in R$
 * (FR-039) and "Revisar compra". Review: amount, balance, the labeled estimate that follows the quote (FR-015/FR-016),
 * the pause of the quote and "Confirmar compra", sent once (FR-043). After sending: only the result of the API
 * (FR-017), a definitive error (with "Depositar" on insufficient balance and "Tentar de novo" when the quote or the
 * service was unavailable) or the uncertain result without resend (FR-041). The focus goes to the title of each step
 * (FR-046); the title suffix is set by the destination. Stateless.
 */
@Composable
fun PurchaseScreen(
    state: PurchaseUiState,
    actions: PurchaseActions,
) {
    // The form shown again after a review or a result takes the focus on its field ("Alterar valor", "Nova compra").
    var leftEditing by remember { mutableStateOf(false) }
    val operation = state.operation
    SideEffect { if (operation !is OperationState.Editing) leftEditing = true }
    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .widthIn(max = MaxContentWidth)
                .padding(bottom = PageBottomPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PageHeading(stringResource(Res.string.page_purchase))

        // One call site for reviewing and sending, so the review keeps its identity (and focus) between them.
        val review =
            when (operation) {
                is OperationState.Reviewing -> ReviewModel(operation.amount, isSubmitting = false)
                is OperationState.Submitting -> ReviewModel(operation.amount, isSubmitting = true)
                else -> null
            }
        if (review != null) PurchaseReview(review, state, actions)

        when (operation) {
            is OperationState.Editing -> {
                PurchaseForm(operation, state, focusOnShow = leftEditing, actions = actions)
            }

            is OperationState.Reviewing, is OperationState.Submitting -> {}

            is OperationState.Succeeded -> {
                PurchaseSucceeded(operation.result, actions.onNewOperation)
            }

            is OperationState.Rejected -> {
                PurchaseRejected(operation.error, state.balance, actions)
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

@Composable
private fun PurchaseForm(
    editing: OperationState.Editing,
    state: PurchaseUiState,
    focusOnShow: Boolean,
    actions: PurchaseActions,
) {
    val fieldFocus = remember { FocusRequester() }
    // Focus on the field after every refused review or refusal of the API, even when the error repeats (FR-046).
    LaunchedEffect(state.attempt, editing.inputError) { if (editing.inputError != null) fieldFocus.requestFocus() }
    LaunchedEffect(Unit) { if (focusOnShow) fieldFocus.requestFocus() }

    BalanceBlock(state.balance, actions.page.onRetryBalance)
    SectionCard {
        AmountField(
            value = editing.input,
            onValueChange = actions.onAmountChange,
            label = stringResource(Res.string.purchase_amount),
            error = editing.inputError?.let { inputErrorMessage(it) },
            focusRequester = fieldFocus,
            onDone = actions.onReview,
        )
        SubmitButton(
            text = stringResource(Res.string.purchase_review),
            isSubmitting = false,
            onClick = actions.onReview,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurchaseReview(
    review: ReviewModel,
    state: PurchaseUiState,
    actions: PurchaseActions,
) {
    FocusOnArrival(enabled = true) { arrivalModifier ->
        Text(
            text = stringResource(Res.string.purchase_review),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }.then(arrivalModifier),
        )
    }
    PauseToggle(isPaused = state.isAutoRefreshPaused, onToggle = actions.page.onToggleAutoRefresh)
    ResultLine(stringResource(Res.string.purchase_amount), review.amount.format())
    BalanceBlock(state.balance, actions.page.onRetryBalance)
    EstimateCard(
        display = estimateDisplay(state.estimate, state.quote),
        priceLabel = stringResource(Res.string.estimate_sell_quote),
        notice = stringResource(Res.string.purchase_estimate_notice),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The decision is the API's: the confirmation stays enabled without a quote or below 1 satoshi (FR-016).
        SubmitButton(
            text = stringResource(Res.string.purchase_confirm),
            isSubmitting = review.isSubmitting,
            onClick = actions.onConfirm,
        )
        if (!review.isSubmitting) {
            SecondaryButton(
                onClick = actions.onEdit,
                modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline(),
            ) {
                Text(stringResource(Res.string.purchase_edit))
            }
        }
    }
}

@Composable
private fun estimateDisplay(
    estimate: BuyEstimate?,
    quote: QuoteState,
): EstimateDisplay {
    if (estimate != null) {
        val warnings =
            if (estimate.belowOneSatoshi) listOf(stringResource(Res.string.purchase_below_one_satoshi)) else emptyList()
        return EstimateDisplay.Ready(estimate.btc, estimate.quote.sellPrice, estimate.quote.quotedAt, warnings)
    }
    return when (quote) {
        QuoteState.Unavailable -> EstimateDisplay.QuoteUnavailable
        is QuoteState.Failed -> EstimateDisplay.Failed(quote.error)
        QuoteState.Loading, is QuoteState.Available -> EstimateDisplay.Loading
    }
}

@Composable
private fun PurchaseSucceeded(
    result: PurchaseResult,
    onNewPurchase: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusOnArrival(enabled = true) { arrivalModifier ->
                Text(
                    text = stringResource(Res.string.purchase_result_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }.then(arrivalModifier),
                )
            }
            ResultLine(stringResource(Res.string.purchase_invested), result.amount.format())
            ResultLine(stringResource(Res.string.purchase_btc), result.btcAmount.format())
            ResultLine(stringResource(Res.string.purchase_quote_used), result.quote.format())
            ResultLine(
                label = stringResource(Res.string.purchase_purchased_at),
                value = result.purchasedAt.format() + " " + stringResource(Res.string.brasilia_time),
            )
            ResultLine(stringResource(Res.string.purchase_new_balance), result.balance.format())
        }
    }
    PrimaryButton(onClick = onNewPurchase, modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline()) {
        Text(stringResource(Res.string.purchase_new))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurchaseRejected(
    error: ApiError,
    balance: Loadable<Money>,
    actions: PurchaseActions,
) {
    val retryable = error == ApiError.QuoteUnavailable || error == ApiError.ServiceUnavailable
    ErrorBlock(
        message = error.message(ErrorContext.Purchase),
        onRetry = if (retryable) actions.onRetry else null,
        focusOnShow = true,
    )
    if (error == ApiError.InsufficientBalance) {
        // The balance was read again after the refusal (US4-4).
        LoadableBlock(balance, onRetry = actions.page.onRetryBalance) { current ->
            Text(
                text = stringResource(Res.string.error_available_balance, current.format()),
                style = MaterialTheme.typography.bodyLarge.numeric(),
            )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val buttonModifier = Modifier.heightIn(min = MinTouchTarget).focusOutline()
        if (error == ApiError.InsufficientBalance) {
            PrimaryButton(onClick = { actions.page.onNavigate(AppRoute.Deposit) }, modifier = buttonModifier) {
                Text(stringResource(Res.string.purchase_deposit))
            }
        }
        SecondaryButton(onClick = actions.onEdit, modifier = buttonModifier) {
            Text(stringResource(Res.string.purchase_edit))
        }
    }
}

@Composable
private fun BalanceBlock(
    balance: Loadable<Money>,
    onRetry: () -> Unit,
) {
    SectionCard(
        title = stringResource(Res.string.purchase_available_balance),
        color = LocalExtendedColors.current.tint,
    ) {
        LoadableBlock(balance, onRetry = onRetry) { value ->
            Text(value.format(), style = MaterialTheme.typography.headlineSmall.numeric())
        }
    }
}

@Composable
private fun ResultLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(value, style = MaterialTheme.typography.headlineSmall.numeric())
    }
}

/** Local refusals show the INVALID_AMOUNT message; a refusal of the API adds the 1 satoshi complement (FR-016). */
@Composable
private fun inputErrorMessage(error: OperationInputError): String =
    when (error) {
        is OperationInputError.Invalid -> stringResource(Res.string.error_invalid_amount)
        OperationInputError.RefusedByApi -> ApiError.InvalidAmount.message(ErrorContext.Purchase)
    }

/** What the review shows while reviewing or sending. */
private data class ReviewModel(
    val amount: Money,
    val isSubmitting: Boolean,
)

private val MaxContentWidth = 640.dp
