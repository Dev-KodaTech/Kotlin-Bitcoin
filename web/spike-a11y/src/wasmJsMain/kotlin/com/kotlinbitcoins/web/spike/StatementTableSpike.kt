package com.kotlinbitcoins.web.spike

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class Entry(
    val type: String,
    val amount: String,
    val btc: String,
    val quote: String,
    val dateTime: String,
)

private data class RedemptionGroup(
    val total: String,
    val entries: List<Entry>,
)

private const val ROW_COUNT = 30
private const val GROUP_POSITION = 10
private const val DAYS = 28
private const val ROW_KINDS = 3
private val COLUMNS = listOf("Tipo", "Valor", "BTC", "Cotação", "Data/hora")

private val rows: List<Entry> =
    (1..ROW_COUNT).map { index ->
        val day = (index % DAYS + 1).toString().padStart(2, '0')
        when (index % ROW_KINDS) {
            0 -> Entry("Depósito", "R$ 1.000,00", "—", "—", "$day/10/2026 09:00")
            1 -> Entry("Compra", "R$ 400,00", "0,00100000 BTC", "R$ 400.000,00", "$day/10/2026 08:10")
            else -> Entry("Compra", "R$ 250,50", "0,00062500 BTC", "R$ 400.800,00", "$day/10/2026 14:35")
        }
    }

private val redemption =
    RedemptionGroup(
        total = "R$ 1.200,00",
        entries =
            listOf(
                Entry("Venda", "R$ 1.000,00", "0,00250000 BTC", "R$ 400.000,00", "15/10/2026 10:00"),
                Entry(
                    "Reinvestimento — não altera o saldo",
                    "R$ 200,00",
                    "0,00050000 BTC",
                    "R$ 400.000,00",
                    "15/10/2026 10:00",
                ),
            ),
    )

private enum class Variant(
    val label: String,
) {
    Table("Tabela (cabeçalhos e células)"),
    Cards("Cartões com texto completo"),
}

/**
 * Statement-like list (T018, criterion A7): 30 rows plus one redemption group in a LazyColumn, in two variants:
 * (a) tabular layout with collection semantics, (b) cards with merged semantics and full text (W-14 fallback).
 */
@Composable
fun StatementTableSpike(
    filter: String?,
    onApplyFilter: (String) -> Unit,
) {
    var variant by remember { mutableStateOf(Variant.Cards) }
    var filterInput by remember(filter) { mutableStateOf(filter.orEmpty()) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { PageHeading("Extrato") }
            item { LiveRegionSpike() }
            item { Text(compatibilityProbe(), style = MaterialTheme.typography.bodySmall) }
            item { Text("Filtro aplicado: ${filter ?: "nenhum"}") }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = filterInput,
                        onValueChange = { filterInput = it },
                        label = { Text("Filtro") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).accessibleField("Filtro", null),
                    )
                    Button(onClick = { onApplyFilter(filterInput) }, modifier = Modifier.focusOutline()) {
                        Text("Aplicar")
                    }
                }
            }
            item { VariantSelector(variant) { variant = it } }
            when (variant) {
                Variant.Table -> tableVariant()
                Variant.Cards -> cardsVariant()
            }
        }
    }
}

@Composable
private fun VariantSelector(
    selected: Variant,
    onSelect: (Variant) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        Text("Apresentação", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Variant.entries.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) }
                        .focusOutline()
                        .padding(4.dp),
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Text(option.label, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

// Variant (a): one header row and one row per entry, exposed as a collection (rows × columns).
private fun LazyListScope.tableVariant() {
    val totalRows = ROW_COUNT + redemption.entries.size + 1
    item {
        Row(
            Modifier
                .fillMaxWidth()
                .semantics { collectionInfo = CollectionInfo(rowCount = totalRows, columnCount = COLUMNS.size) },
        ) {
            COLUMNS.forEachIndexed { column, title ->
                Cell(row = 0, column = column, isHeader = true, text = title)
            }
        }
        HorizontalDivider()
    }
    val all = rows.take(GROUP_POSITION) + redemption.entries + rows.drop(GROUP_POSITION)
    items(all.withIndex().toList()) { (index, entry) ->
        Row(Modifier.fillMaxWidth().focusable().focusOutline()) {
            listOf(entry.type, entry.amount, entry.btc, entry.quote, entry.dateTime).forEachIndexed { column, text ->
                Cell(row = index + 1, column = column, isHeader = false, text = text)
            }
        }
    }
}

@Composable
private fun RowScope.Cell(
    row: Int,
    column: Int,
    isHeader: Boolean,
    text: String,
) {
    Text(
        text = text,
        fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
        style = MaterialTheme.typography.bodySmall,
        modifier =
            Modifier
                .weight(1f)
                .padding(4.dp)
                .semantics {
                    collectionItemInfo = CollectionItemInfo(row, 1, column, 1)
                    if (isHeader) heading()
                },
    )
}

// Variant (b): each entry is a focusable card read at once with self-explanatory text.
private fun LazyListScope.cardsVariant() {
    items(rows.take(GROUP_POSITION)) { EntryCard(it) }
    item { RedemptionGroupCard(redemption) }
    items(rows.drop(GROUP_POSITION)) { EntryCard(it) }
}

@Composable
private fun EntryCard(entry: Entry) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusOutline()
                .focusable()
                .clearAndSetSemantics { contentDescription = entry.spokenText() },
    ) {
        EntryText(entry, Modifier.padding(12.dp))
    }
}

/** Full, self-explanatory sentence read at once (W-14 fallback). */
private fun Entry.spokenText(): String =
    buildString {
        append("$type de $amount")
        if (btc != "—") append(", $btc, cotação $quote")
        append(", $dateTime, horário de Brasília")
    }

@Composable
private fun EntryText(
    entry: Entry,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text("${entry.type} de ${entry.amount}", fontWeight = FontWeight.SemiBold)
        if (entry.btc != "—") {
            Text("${entry.btc}, cotação ${entry.quote}")
        }
        Text("${entry.dateTime} (horário de Brasília)")
    }
}

@Composable
private fun RedemptionGroupCard(group: RedemptionGroup) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Resgate — total ${group.total}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            group.entries.forEach { entry ->
                Box(
                    Modifier
                        .padding(start = 16.dp, top = 8.dp)
                        .focusOutline()
                        .focusable()
                        .clearAndSetSemantics { contentDescription = entry.spokenText() },
                ) {
                    EntryText(entry)
                }
            }
        }
    }
}
