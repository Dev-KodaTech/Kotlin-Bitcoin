package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Title of a card with a link to the page that has everything about it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardHeader(
    title: String,
    link: String,
    onLink: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle(title)
        TextButton(onClick = onLink, modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline()) {
            Text(link)
        }
    }
}

/** The rows of a card, one under the other with a thin line between them. */
@Composable
fun <T> DividedRows(
    items: List<T>,
    row: @Composable (T) -> Unit,
) {
    Column {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            row(item)
        }
    }
}

/** The one line a card shows instead of its rows (nothing to show, or why). */
@Composable
fun MutedText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = CardPadding / 2),
    )
}
