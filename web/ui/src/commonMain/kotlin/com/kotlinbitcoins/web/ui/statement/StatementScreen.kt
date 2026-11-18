package com.kotlinbitcoins.web.ui.statement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.statement.GroupedStatement
import com.kotlinbitcoins.web.core.statement.StatementItem
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.state.statement.StatementPeriod
import com.kotlinbitcoins.web.state.statement.StatementUiState
import com.kotlinbitcoins.web.ui.components.ErrorBlock
import com.kotlinbitcoins.web.ui.components.FormTextField
import com.kotlinbitcoins.web.ui.components.LoadingIndicator
import com.kotlinbitcoins.web.ui.components.LocalDenseLayout
import com.kotlinbitcoins.web.ui.components.MinTouchTarget
import com.kotlinbitcoins.web.ui.components.PageBottomPadding
import com.kotlinbitcoins.web.ui.components.PageHeading
import com.kotlinbitcoins.web.ui.components.PrimaryButton
import com.kotlinbitcoins.web.ui.components.SecondaryButton
import com.kotlinbitcoins.web.ui.components.SectionCard
import com.kotlinbitcoins.web.ui.components.focusOutline
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.brasilia_time
import com.kotlinbitcoins.web.ui.resources.error_invalid_date_range
import com.kotlinbitcoins.web.ui.resources.page_statement
import com.kotlinbitcoins.web.ui.resources.statement_apply
import com.kotlinbitcoins.web.ui.resources.statement_clear
import com.kotlinbitcoins.web.ui.resources.statement_date_hint
import com.kotlinbitcoins.web.ui.resources.statement_empty
import com.kotlinbitcoins.web.ui.resources.statement_entries
import com.kotlinbitcoins.web.ui.resources.statement_filter
import com.kotlinbitcoins.web.ui.resources.statement_from
import com.kotlinbitcoins.web.ui.resources.statement_period
import com.kotlinbitcoins.web.ui.resources.statement_to
import com.kotlinbitcoins.web.ui.shared.message
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.stringResource

private val DateFieldWidth = 200.dp

/**
 * Extrato (US7): h1, the period really queried (FR-026), the filter form with two dd/mm/aaaa fields whose invalid
 * range error is attached to both (FR-030), "Aplicar filtro"/"Limpar filtro" and one card per item, newest first, the
 * lines of a redemption together (FR-029). Loading, empty (with the period, US7-7) and error are distinct; on an
 * invalid range the previous statement stays visible. Stateless.
 */
@Composable
fun StatementScreen(
    state: StatementUiState,
    actions: StatementActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = PageBottomPadding),
        verticalArrangement = Arrangement.spacedBy(if (LocalDenseLayout.current) 12.dp else 16.dp),
    ) {
        item { PageHeading(stringResource(Res.string.page_statement)) }
        item { FilterForm(state, actions) }
        state.period?.let { period -> item { PeriodText(period) } }
        statementContent(state.statement, state.period, actions)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterForm(
    state: StatementUiState,
    actions: StatementActions,
) {
    val fromFocus = remember { FocusRequester() }
    val toFocus = remember { FocusRequester() }
    val error = state.filterError?.let { stringResource(Res.string.error_invalid_date_range) }
    val hint = stringResource(Res.string.statement_date_hint)
    // A refused apply (never the error of the route on arrival, when the h1 has the focus) brings the focus here.
    val initialErrorId = remember { state.filterErrorId }
    LaunchedEffect(state.filterErrorId) {
        if (state.filterErrorId != initialErrorId && state.filterError != null) fromFocus.requestFocus()
    }
    SectionCard(title = stringResource(Res.string.statement_filter)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FormTextField(
                value = state.filterInput.from,
                onValueChange = actions.onFromChange,
                label = stringResource(Res.string.statement_from),
                error = error,
                hint = hint,
                autofill = null,
                focusRequester = fromFocus,
                onImeAction = { toFocus.requestFocus() },
                modifier = Modifier.widthIn(max = DateFieldWidth),
            )
            FormTextField(
                value = state.filterInput.to,
                onValueChange = actions.onToChange,
                label = stringResource(Res.string.statement_to),
                error = error,
                hint = hint,
                autofill = null,
                imeAction = ImeAction.Done,
                focusRequester = toFocus,
                onImeAction = actions.onApplyFilter,
                modifier = Modifier.widthIn(max = DateFieldWidth),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton(
                onClick = actions.onApplyFilter,
                modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline(),
            ) { Text(stringResource(Res.string.statement_apply)) }
            SecondaryButton(
                onClick = actions.onClearFilter,
                modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline(),
            ) { Text(stringResource(Res.string.statement_clear)) }
        }
    }
}

@Composable
private fun PeriodText(period: StatementPeriod) {
    Text(
        text =
            stringResource(Res.string.statement_period, period.from.format(), period.to.format()) + " " +
                stringResource(Res.string.brasilia_time),
        style = MaterialTheme.typography.bodyLarge.numeric(),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun LazyListScope.statementContent(
    statement: Loadable<GroupedStatement>,
    period: StatementPeriod?,
    actions: StatementActions,
) {
    when (statement) {
        Loadable.Loading -> {
            item { LoadingIndicator() }
        }

        Loadable.Empty -> {
            item {
                Text(
                    text =
                        stringResource(
                            Res.string.statement_empty,
                            period?.from?.format().orEmpty(),
                            period?.to?.format().orEmpty(),
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        is Loadable.Failed -> {
            item { ErrorBlock(statement.error.message(), onRetry = actions.onRetry) }
        }

        is Loadable.Loaded -> {
            item {
                Text(
                    text = stringResource(Res.string.statement_entries),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(statement.value.items, key = ::itemKey) { item ->
                when (item) {
                    is StatementItem.Single -> StatementEntryCard(item)
                    is StatementItem.RedemptionGroup -> RedemptionGroupCard(item)
                }
            }
        }
    }
}

private fun itemKey(item: StatementItem): String =
    when (item) {
        is StatementItem.Single -> item.entry.id
        is StatementItem.RedemptionGroup -> "group-" + item.requestId
    }
