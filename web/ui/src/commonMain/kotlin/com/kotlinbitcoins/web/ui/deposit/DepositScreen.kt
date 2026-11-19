package com.kotlinbitcoins.web.ui.deposit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.model.DepositResult
import com.kotlinbitcoins.web.core.model.StatementEntry
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.deposit.DepositPreview
import com.kotlinbitcoins.web.state.deposit.DepositUiState
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.state.shared.OperationInputError
import com.kotlinbitcoins.web.state.shared.OperationState
import com.kotlinbitcoins.web.ui.components.AmountField
import com.kotlinbitcoins.web.ui.components.AppCard
import com.kotlinbitcoins.web.ui.components.CardHeader
import com.kotlinbitcoins.web.ui.components.CardPadding
import com.kotlinbitcoins.web.ui.components.DividedRows
import com.kotlinbitcoins.web.ui.components.ErrorBlock
import com.kotlinbitcoins.web.ui.components.FocusOnArrival
import com.kotlinbitcoins.web.ui.components.HeroOutlinedButton
import com.kotlinbitcoins.web.ui.components.LoadableBlock
import com.kotlinbitcoins.web.ui.components.LoadingIndicator
import com.kotlinbitcoins.web.ui.components.MinTouchTarget
import com.kotlinbitcoins.web.ui.components.MutedText
import com.kotlinbitcoins.web.ui.components.PageBottomPadding
import com.kotlinbitcoins.web.ui.components.PageHeading
import com.kotlinbitcoins.web.ui.components.PrimaryButton
import com.kotlinbitcoins.web.ui.components.SecondaryButton
import com.kotlinbitcoins.web.ui.components.SectionCard
import com.kotlinbitcoins.web.ui.components.SubmitButton
import com.kotlinbitcoins.web.ui.components.UncertainOutcomeBlock
import com.kotlinbitcoins.web.ui.components.focusOutline
import com.kotlinbitcoins.web.ui.icons.AppIcons
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.brasilia_time
import com.kotlinbitcoins.web.ui.resources.dashboard_recent_description
import com.kotlinbitcoins.web.ui.resources.deposit_amount
import com.kotlinbitcoins.web.ui.resources.deposit_amount_deposited
import com.kotlinbitcoins.web.ui.resources.deposit_balance_after
import com.kotlinbitcoins.web.ui.resources.deposit_balance_updated
import com.kotlinbitcoins.web.ui.resources.deposit_buy
import com.kotlinbitcoins.web.ui.resources.deposit_current_balance
import com.kotlinbitcoins.web.ui.resources.deposit_intro
import com.kotlinbitcoins.web.ui.resources.deposit_new
import com.kotlinbitcoins.web.ui.resources.deposit_new_balance
import com.kotlinbitcoins.web.ui.resources.deposit_no_estimate
import com.kotlinbitcoins.web.ui.resources.deposit_occurred_at
import com.kotlinbitcoins.web.ui.resources.deposit_plus_amount
import com.kotlinbitcoins.web.ui.resources.deposit_quick_amount_selected
import com.kotlinbitcoins.web.ui.resources.deposit_quick_amounts
import com.kotlinbitcoins.web.ui.resources.deposit_recent
import com.kotlinbitcoins.web.ui.resources.deposit_recent_empty
import com.kotlinbitcoins.web.ui.resources.deposit_result_title
import com.kotlinbitcoins.web.ui.resources.deposit_submit
import com.kotlinbitcoins.web.ui.resources.deposit_view_statement
import com.kotlinbitcoins.web.ui.resources.error_invalid_amount
import com.kotlinbitcoins.web.ui.resources.page_deposit
import com.kotlinbitcoins.web.ui.resources.retry
import com.kotlinbitcoins.web.ui.resources.statement_type_deposit
import com.kotlinbitcoins.web.ui.shared.message
import com.kotlinbitcoins.web.ui.theme.LocalExtendedColors
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.stringResource

/** From this width on, the balance and the newest deposits go to a column on the right of the form. */
private val TwoColumnsMinWidth = 760.dp
private val MaxContentWidth = 1040.dp
private val SectionGap = 20.dp
private val SuccessWellSize = 56.dp
private const val MAIN_COLUMN_WEIGHT = 1.5f
private const val HERO_DIVIDER_ALPHA = 0.32f

/**
 * Deposit (US3): h1 "Depositar"; the amount in R$ (FR-039) with shortcuts that fill it and "Depositar", sent once
 * (FR-043); and, in a column on the right on wide screens, the balance as the hero block with the labeled estimate
 * of the balance after the deposit, and the newest deposits. Each block has its own state (FR-042). After sending:
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
    BoxWithConstraints(modifier = Modifier.widthIn(max = MaxContentWidth)) {
        val twoColumns = maxWidth >= TwoColumnsMinWidth
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = PageBottomPadding),
            verticalArrangement = Arrangement.spacedBy(SectionGap),
        ) {
            Header()
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(SectionGap)) {
                    Column(
                        modifier = Modifier.weight(MAIN_COLUMN_WEIGHT),
                        verticalArrangement = Arrangement.spacedBy(SectionGap),
                    ) { OperationBlocks(state, focusFormOnShow = hadOutcome, actions = actions) }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SectionGap),
                    ) { SummaryBlocks(state, actions) }
                }
            } else {
                OperationBlocks(state, focusFormOnShow = hadOutcome, actions = actions)
                SummaryBlocks(state, actions)
            }
        }
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PageHeading(stringResource(Res.string.page_deposit))
        Text(
            text = stringResource(Res.string.deposit_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The form while editing or sending; then the result, the definitive error or the uncertain result. */
@Composable
private fun OperationBlocks(
    state: DepositUiState,
    focusFormOnShow: Boolean,
    actions: DepositActions,
) {
    val operation = state.operation
    // One call site for editing and sending, so the field keeps its identity (and focus) between them.
    val form =
        when (operation) {
            is OperationState.Editing -> FormModel(operation.input, operation.inputError, isSubmitting = false)
            is OperationState.Submitting -> FormModel(operation.amount.toPtBrInput(), null, isSubmitting = true)
            else -> null
        }
    if (form != null) DepositForm(form, state, focusOnShow = focusFormOnShow, actions = actions)

    when (operation) {
        is OperationState.Editing, is OperationState.Submitting -> {}

        // The deposit has no review step: the machine never enters it.
        is OperationState.Reviewing -> {}

        is OperationState.Succeeded -> {
            DepositSucceeded(operation.result, actions)
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
                onViewBalance = { actions.page.onNavigate(AppRoute.Dashboard) },
                onViewStatement = { actions.page.onNavigate(AppRoute.Statement()) },
                announce = actions.page.announce,
            )
        }
    }
}

@Composable
private fun DepositForm(
    form: FormModel,
    state: DepositUiState,
    focusOnShow: Boolean,
    actions: DepositActions,
) {
    val fieldFocus = remember { FocusRequester() }
    // Focus on the field after every refused confirmation, even when the error repeats (FR-046).
    LaunchedEffect(state.attempt, form.inputError) { if (form.inputError != null) fieldFocus.requestFocus() }
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
        QuickAmounts(
            amounts = state.quickAmounts,
            selected = state.preview?.amount,
            enabled = !form.isSubmitting,
            onPick = { actions.onAmountChange(it.toInputString()) },
        )
        SubmitButton(
            text = stringResource(Res.string.deposit_submit),
            isSubmitting = form.isSubmitting,
            onClick = actions.onConfirm,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Shortcuts that fill the field; the one equal to the amount in the field is filled like the current page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAmounts(
    amounts: List<Money>,
    selected: Money?,
    enabled: Boolean,
    onPick: (Money) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.deposit_quick_amounts),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            amounts.forEach { amount ->
                QuickAmountButton(
                    label = amount.format(),
                    isSelected = amount == selected,
                    enabled = enabled,
                    onClick = { onPick(amount) },
                )
            }
        }
    }
}

@Composable
private fun QuickAmountButton(
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val extended = LocalExtendedColors.current
    val scheme = MaterialTheme.colorScheme
    // L6 of W-00: the mirror drops `selected`, so the selected amount is also part of the name.
    val selectedName = stringResource(Res.string.deposit_quick_amount_selected, label)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (isSelected) extended.hero else extended.tint,
                contentColor = if (isSelected) extended.onHero else scheme.onPrimaryContainer,
            ),
        modifier =
            Modifier
                .heightIn(min = MinTouchTarget)
                .focusOutline(if (isSelected) scheme.onSurface else Color.Unspecified)
                .semantics {
                    selected = isSelected
                    if (isSelected) contentDescription = selectedName
                },
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.numeric())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DepositSucceeded(
    result: DepositResult,
    actions: DepositActions,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(SuccessWellSize).background(LocalExtendedColors.current.tint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                FocusOnArrival(enabled = true) { arrivalModifier ->
                    Text(
                        text = stringResource(Res.string.deposit_result_title, result.amount.format()),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).semantics { heading() }.then(arrivalModifier),
                    )
                }
            }
            Column {
                val divider = MaterialTheme.colorScheme.outlineVariant
                HorizontalDivider(color = divider)
                ResultRow(stringResource(Res.string.deposit_amount_deposited), result.amount.format())
                HorizontalDivider(color = divider)
                ResultRow(stringResource(Res.string.deposit_new_balance), result.balance.format())
                HorizontalDivider(color = divider)
                ResultRow(
                    label = stringResource(Res.string.deposit_occurred_at),
                    value = result.occurredAt.format() + " " + stringResource(Res.string.brasilia_time),
                )
                HorizontalDivider(color = divider)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val buttonModifier = Modifier.heightIn(min = MinTouchTarget).focusOutline()
                PrimaryButton(onClick = { actions.page.onNavigate(AppRoute.Purchase) }, modifier = buttonModifier) {
                    Text(stringResource(Res.string.deposit_buy))
                }
                SecondaryButton(onClick = actions.onNewOperation, modifier = buttonModifier) {
                    Text(stringResource(Res.string.deposit_new))
                }
            }
        }
    }
}

/** One value of the result: its name on the left and the value of the API on the right (below, when narrow). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultRow(
    label: String,
    value: String,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Text(value, style = MaterialTheme.typography.titleMedium.numeric())
    }
}

@Composable
private fun SummaryBlocks(
    state: DepositUiState,
    actions: DepositActions,
) {
    BalanceHero(state, actions.page.onRetryBalance)
    SectionCard {
        CardHeader(
            title = stringResource(Res.string.deposit_recent),
            link = stringResource(Res.string.deposit_view_statement),
            onLink = { actions.page.onNavigate(AppRoute.Statement()) },
        )
        LoadableBlock(
            state = state.recentDeposits,
            onRetry = actions.page.onRetryRecentDeposits,
            empty = { MutedText(stringResource(Res.string.deposit_recent_empty)) },
        ) { deposits ->
            DividedRows(deposits) { RecentDepositRow(it) }
        }
    }
}

/**
 * The balance on the filled block of the page. While there is a form, also the estimate of the balance after the
 * deposit; after a deposit, the balance is already the one the API answered.
 */
@Composable
private fun BalanceHero(
    state: DepositUiState,
    onRetryBalance: () -> Unit,
) {
    val extended = LocalExtendedColors.current
    Surface(
        color = extended.hero,
        contentColor = extended.onHero,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(Res.string.deposit_current_balance),
                    style = MaterialTheme.typography.titleSmall,
                    color = extended.onHeroMuted,
                    modifier = Modifier.semantics { heading() },
                )
                when (val balance = state.balance) {
                    Loadable.Loading -> {
                        LoadingIndicator()
                    }

                    Loadable.Empty -> {}

                    is Loadable.Loaded -> {
                        Text(balance.value.format(), style = MaterialTheme.typography.headlineMedium.numeric())
                    }

                    is Loadable.Failed -> {
                        Text(balance.error.message(), style = MaterialTheme.typography.bodyLarge)
                        HeroOutlinedButton(stringResource(Res.string.retry), onRetryBalance)
                    }
                }
            }
            when (val operation = state.operation) {
                is OperationState.Editing, is OperationState.Submitting -> {
                    BalanceEstimate(state.preview)
                }

                is OperationState.Succeeded -> {
                    HorizontalDivider(color = extended.onHero.copy(alpha = HERO_DIVIDER_ALPHA))
                    Text(
                        text = stringResource(Res.string.deposit_balance_updated, operation.result.amount.format()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = extended.onHeroMuted,
                    )
                }

                is OperationState.Reviewing, is OperationState.Rejected, OperationState.Uncertain -> {}
            }
        }
    }
}

/**
 * "Estimativa do saldo após este depósito": the amount in the field as a white pill and the balance with it. Always
 * in place, so that the block does not change its structure while the person types; a dash without a valid amount
 * or without the balance.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BalanceEstimate(preview: DepositPreview?) {
    val extended = LocalExtendedColors.current
    HorizontalDivider(color = extended.onHero.copy(alpha = HERO_DIVIDER_ALPHA))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.deposit_balance_after),
                style = MaterialTheme.typography.bodyMedium,
                color = extended.onHeroMuted,
                modifier = Modifier.semantics { heading() },
            )
            if (preview != null) {
                Surface(color = extended.heroAction, contentColor = extended.onHeroAction, shape = CircleShape) {
                    Text(
                        text = stringResource(Res.string.deposit_plus_amount, preview.amount.format()),
                        style = MaterialTheme.typography.labelLarge.numeric(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Text(
            text = preview?.balanceAfter?.format() ?: stringResource(Res.string.deposit_no_estimate),
            style = MaterialTheme.typography.titleLarge.numeric(),
        )
    }
}

/** One deposit of the statement, read at once: type, signed amount and when. */
@Composable
private fun RecentDepositRow(entry: StatementEntry) {
    val amount = stringResource(Res.string.deposit_plus_amount, entry.amount.format())
    val description =
        stringResource(
            Res.string.dashboard_recent_description,
            stringResource(Res.string.statement_type_deposit),
            amount,
            entry.occurredAt.format(),
            stringResource(Res.string.brasilia_time),
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.occurredAt.format(),
            style = MaterialTheme.typography.bodyMedium.numeric(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(amount, style = MaterialTheme.typography.titleSmall.numeric())
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
