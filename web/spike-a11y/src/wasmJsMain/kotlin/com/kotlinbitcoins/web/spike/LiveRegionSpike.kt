package com.kotlinbitcoins.web.spike

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private const val UNAVAILABLE = "Cotação indisponível no momento"
private const val RESTORED = "Cotação restabelecida"
private val REFRESH_INTERVAL = 10.seconds

/**
 * Live region comparison (T019, criterion A6): technique A uses Compose `liveRegion`, technique B writes to the
 * `#live-polite` region outside the canvas. The 10 s counter changes without any announcement (FR-047).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveRegionSpike() {
    var isComposeAvailable by remember { mutableStateOf(true) }
    var isDomAvailable by remember { mutableStateOf(true) }
    var cycles by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_INTERVAL)
            cycles++
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Cotação",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text("Atualizações automáticas: $cycles")
        Text(
            text = "Técnica A (liveRegion do Compose): " + if (isComposeAvailable) RESTORED else UNAVAILABLE,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text("Técnica B (aria-live fora do canvas): " + if (isDomAvailable) RESTORED else UNAVAILABLE)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { isComposeAvailable = !isComposeAvailable },
                modifier = Modifier.focusOutline(),
            ) {
                Text("Alternar cotação (técnica A)")
            }
            OutlinedButton(
                onClick = {
                    isDomAvailable = !isDomAvailable
                    DomAnnouncer.polite(if (isDomAvailable) RESTORED else UNAVAILABLE)
                },
                modifier = Modifier.focusOutline(),
            ) {
                Text("Alternar cotação (técnica B)")
            }
        }
    }
}
