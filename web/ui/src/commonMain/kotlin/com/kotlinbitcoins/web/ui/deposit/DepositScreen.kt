package com.kotlinbitcoins.web.ui.deposit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.kotlinbitcoins.web.core.model.DepositResult
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.deposit.DepositUiState
import com.kotlinbitcoins.web.state.shared.OperationInputError
import com.kotlinbitcoins.web.state.shared.OperationState
import com.kotlinbitcoins.web.ui.components.AmountField
import com.kotlinbitcoins.web.ui.components.AppCard
import com.kotlinbitcoins.web.ui.components.CardPadding
import com.kotlinbitcoins.web.ui.components.ErrorBlock
import com.kotlinbitcoins.web.ui.components.FocusOnArrival
import com.kotlinbitcoins.web.ui.components.MinTouchTarget
import com.kotlinbitcoins.web.ui.components.PageBottomPadding
import com.kotlinbitcoins.web.ui.components.PageHeading
import com.kotlinbitcoins.web.ui.components.PrimaryButton
import com.kotlinbitcoins.web.ui.components.SectionCard
import com.kotlinbitcoins.web.ui.components.SubmitButton
import com.kotlinbitcoins.web.ui.components.UncertainOutcomeBlock
import com.kotlinbitcoins.web.ui.components.focusOutline
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.brasilia_time
import com.kotlinbitcoins.web.ui.resources.deposit_amount
import com.kotlinbitcoins.web.ui.resources.deposit_new
import com.kotlinbitcoins.web.ui.resources.deposit_new_balance
import com.kotlinbitcoins.web.ui.resources.deposit_occurred_at
import com.kotlinbitcoins.web.ui.resources.deposit_result_title
import com.kotlinbitcoins.web.ui.resources.deposit_submit
import com.kotlinbitcoins.web.ui.resources.error_invalid_amount
import com.kotlinbitcoins.web.ui.resources.page_deposit
import com.kotlinbitcoins.web.ui.shared.message
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.stringResource

/**
 * Deposit (US3): h1 "Depositar", the amount in R$ (FR-039) and "Depositar", sent once (FR-043). After sending:
 * the result of the API with the focus on its title (FR-046), a definitive error, or the uncertain result without
 * resend (FR-041). The title suffix "Depósito realizado" is set by the destination. Stateless.
 */
@Composable
fun DepositScreen(
    state: DepositUiState,
    actions: DepositActions,
) {
    // A form shown again after a result takes the focus on its field ("Novo depósito").
    var hadOutcome by remember { mutableStateOf(false) }
    val operation = state.operation
    SideEffect {
        if (operation !is OperationState.Editing && operation !is OperationState.Submitting) hadOutcome = true
    }
    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .widthIn(max = MaxContentWidth)
                .padding(bottom = PageBottomPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PageHeading(stringResource(Res.string.page_deposit))

        // One call site for editing and sending, so the field keeps its identity (and focus) between them.
        val form =
            when (operation) {
                is OperationState.Editing -> FormModel(operation.input, operation.inputError, isSubmitting = false)
                is OperationState.Submitting -> FormModel(operation.amount.toPtBrInput(), null, isSubmitting = true)
                else -> null
            }
        if (form != null) {
            DepositForm(form, attempt = state.attempt, focusOnShow = hadOutcome, actions = actions)
        }

        when (operation) {
            is OperationState.Editing, is OperationState.Submitting -> {}

            // The deposit has no review step: the machine never enters it.
            is OperationState.Reviewing -> {}

            is OperationState.Succeeded -> {
                DepositSucceeded(operation.result, actions.onNewOperation)
            }

            is OperationState.Rejected -> {
                ErrorBlock(
                    message = operation.error.message(),
                    onRetry = if (operation.error.isRetryable()) actions.onRetry else actions.onEdit,
                    focusOnShow = true,
                )
            }

            OperationState.Uncertain -> {
                UncertainOutcomeBlock(
                    onViewBalance = { actions.onNavigate(AppRoute.Dashboard) },
                    onViewStatement = { actions.onNavigate(AppRoute.Statement()) },
                    announce = actions.announce,
                )
            }
        }
    }
}

@Composable
private fun DepositForm(
    form: FormModel,
    attempt: Int,
    focusOnShow: Boolean,
    actions: DepositActions,
) {
    val fieldFocus = remember { FocusRequester() }
    // Focus on the field after every refused confirmation, even when the error repeats (FR-046).
    LaunchedEffect(attempt, form.inputError) { if (form.inputError != null) fieldFocus.requestFocus() }
    LaunchedEffect(Unit) { if (focusOnShow) fieldFocus.requestFocus() }

    SectionCard {
        AmountField(
            value = form.input,
            onValueChange = actions.onAmountChange,
            label = stringResource(Res.string.deposit_amount),
            error = form.inputError?.let { stringResource(Res.string.error_invalid_amount) },
            focusRequester = fieldFocus,
            enabled = !form.isSubmitting,
            onDone = actions.onConfirm,
        )
        SubmitButton(
            text = stringResource(Res.string.deposit_submit),
            isSubmitting = form.isSubmitting,
            onClick = actions.onConfirm,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DepositSucceeded(
    result: DepositResult,
    onNewDeposit: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusOnArrival(enabled = true) { arrivalModifier ->
                Text(
                    text = stringResource(Res.string.deposit_result_title, result.amount.format()),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }.then(arrivalModifier),
                )
            }
            ResultLine(
                label = stringResource(Res.string.deposit_new_balance),
                value = result.balance.format(),
            )
            ResultLine(
                label = stringResource(Res.string.deposit_occurred_at),
                value = result.occurredAt.format() + " " + stringResource(Res.string.brasilia_time),
            )
        }
    }
    PrimaryButton(onClick = onNewDeposit, modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline()) {
        Text(stringResource(Res.string.deposit_new))
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

/** What the amount form shows while editing or sending. */
private data class FormModel(
    val input: String,
    val inputError: OperationInputError?,
    val isSubmitting: Boolean,
)

/** A definitive answer that may change in a moment: offer a new attempt that needs a new confirmation. */
private fun ApiError.isRetryable(): Boolean = this == ApiError.ServiceUnavailable || this == ApiError.QuoteUnavailable

/** "1234.56" → "1234,56": the amount being sent, shown in the disabled field. */
private fun Money.toPtBrInput(): String = toApiString().replace('.', ',')

private val MaxContentWidth = 640.dp
