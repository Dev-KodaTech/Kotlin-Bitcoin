package com.kotlinbitcoins.web.ui.statement

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.model.EntryType
import com.kotlinbitcoins.web.core.model.StatementEntry
import com.kotlinbitcoins.web.core.statement.StatementItem
import com.kotlinbitcoins.web.ui.components.focusOutline
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.brasilia_time
import com.kotlinbitcoins.web.ui.resources.statement_amount
import com.kotlinbitcoins.web.ui.resources.statement_btc
import com.kotlinbitcoins.web.ui.resources.statement_entry_btc_description
import com.kotlinbitcoins.web.ui.resources.statement_entry_description
import com.kotlinbitcoins.web.ui.resources.statement_group_description
import com.kotlinbitcoins.web.ui.resources.statement_group_title
import com.kotlinbitcoins.web.ui.resources.statement_no_balance_change
import com.kotlinbitcoins.web.ui.resources.statement_occurred_at
import com.kotlinbitcoins.web.ui.resources.statement_quote
import com.kotlinbitcoins.web.ui.resources.statement_type_deposit
import com.kotlinbitcoins.web.ui.resources.statement_type_purchase
import com.kotlinbitcoins.web.ui.resources.statement_type_redemption
import com.kotlinbitcoins.web.ui.resources.statement_type_reinvestment
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val FieldMinWidth = 140.dp

/**
 * One line of the statement without `requestId` (FR-028): type, amount, date/time and, when there are, BTC and quote.
 * Focusable and read at once as a full sentence (W-14, variant (b) of "W-00 Resultado").
 */
@Composable
fun StatementEntryCard(entry: StatementItem.Single) {
    val description = entryDescription(entry.entry)
    Card(modifier = cardModifier(description)) {
        EntryFields(entry.entry, modifier = Modifier.padding(16.dp))
    }
}

/**
 * The lines of one redemption request together (FR-029): "Resgate — total R$ …" (display sum of the redemption
 * lines only) and every line of the group, the reinvestment marked as not changing the balance. Read at once.
 */
@Composable
fun RedemptionGroupCard(group: StatementItem.RedemptionGroup) {
    val title = stringResource(Res.string.statement_group_title, group.totalRedeemed.format())
    val lines = group.entries.map { entryDescription(it) }
    val description =
        stringResource(
            Res.string.statement_group_description,
            title,
            group.occurredAt.format(),
            stringResource(Res.string.brasilia_time),
            lines.joinToString("; "),
        )
    Card(modifier = cardModifier(description)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.numeric())
            group.entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                EntryFields(entry)
            }
        }
    }
}

private fun cardModifier(description: String): Modifier =
    Modifier
        .fillMaxWidth()
        .focusOutline()
        .focusable()
        .clearAndSetSemantics { contentDescription = description }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryFields(
    entry: StatementEntry,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(typeLabel(entry.type), style = MaterialTheme.typography.titleSmall)
        if (entry.type == EntryType.Reinvestment) {
            Text(
                stringResource(Res.string.statement_no_balance_change),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Field(stringResource(Res.string.statement_amount), entry.amount.format())
            Field(
                stringResource(Res.string.statement_occurred_at),
                "${entry.occurredAt.format()} ${stringResource(Res.string.brasilia_time)}",
            )
            entry.btcAmount?.let { Field(stringResource(Res.string.statement_btc), it.format()) }
            entry.quote?.let { Field(stringResource(Res.string.statement_quote), it.format()) }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.widthIn(min = FieldMinWidth)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge.numeric())
    }
}

/** "Compra de R$ 400,00 em 09/11/2026 08:20 (horário de Brasília), 0,00100000 BTC, cotação R$ 400.000,00" */
@Composable
private fun entryDescription(entry: StatementEntry): String {
    val base =
        stringResource(
            Res.string.statement_entry_description,
            typeLabel(entry.type),
            entry.amount.format(),
            entry.occurredAt.format(),
            stringResource(Res.string.brasilia_time),
        )
    val btc = entry.btcAmount
    val quote = entry.quote
    val market =
        if (btc != null && quote != null) {
            stringResource(Res.string.statement_entry_btc_description, btc.format(), quote.format())
        } else {
            ""
        }
    val balance =
        if (entry.type == EntryType.Reinvestment) ", " + stringResource(Res.string.statement_no_balance_change) else ""
    return base + market + balance
}

@Composable
private fun typeLabel(type: EntryType): String = stringResource(typeResource(type))

private fun typeResource(type: EntryType): StringResource =
    when (type) {
        EntryType.Deposit -> Res.string.statement_type_deposit
        EntryType.Purchase -> Res.string.statement_type_purchase
        EntryType.Redemption -> Res.string.statement_type_redemption
        EntryType.Reinvestment -> Res.string.statement_type_reinvestment
    }
