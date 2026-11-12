package com.kotlinbitcoins.web.spike

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.browser.document

/** Width cap of the content column (W-14). */
val ContentMaxWidth = 720.dp

/**
 * Visible focus indicator drawn on the canvas (criterion A2): 3 dp outline in `primary`, which has ≥ 3:1 contrast
 * against `surface` in both baseline schemes. Material 3's focus state layer alone is too faint.
 */
fun Modifier.focusOutline(): Modifier =
    composed {
        var isFocused by remember { mutableStateOf(false) }
        val color = MaterialTheme.colorScheme.primary
        this
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (isFocused) Modifier.border(3.dp, color, MaterialTheme.shapes.small) else Modifier)
    }

/** Page heading (h1 in the mirror, criterion A8). */
@Composable
fun PageHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )
}

/** Centred column limited to [ContentMaxWidth]; no horizontal scroll at page level (A9). */
@Composable
fun ContentColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(16.dp),
            content = content,
        )
    }
}

/** Real `aria-live` regions outside the canvas, created in index.html (W-09, technique B of LiveRegionSpike). */
object DomAnnouncer {
    fun polite(text: String) = write("live-polite", text)

    fun assertive(text: String) = write("live-assertive", text)

    private fun write(
        id: String,
        text: String,
    ) {
        val region = document.getElementById(id) ?: return
        // Clearing first makes screen readers announce a repeated identical message again.
        region.textContent = ""
        region.textContent = text
    }
}
