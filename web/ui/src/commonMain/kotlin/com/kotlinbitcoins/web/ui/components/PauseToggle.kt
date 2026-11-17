package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.auto_refresh_active
import com.kotlinbitcoins.web.ui.resources.auto_refresh_paused
import com.kotlinbitcoins.web.ui.resources.auto_refresh_state_active
import com.kotlinbitcoins.web.ui.resources.auto_refresh_state_paused
import org.jetbrains.compose.resources.stringResource

/** Pause/resume of the auto-refresh (FR-012), with the state in the visible label and in the semantics. */
@Composable
fun PauseToggle(
    isPaused: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(if (isPaused) Res.string.auto_refresh_paused else Res.string.auto_refresh_active)
    val state =
        stringResource(if (isPaused) Res.string.auto_refresh_state_paused else Res.string.auto_refresh_state_active)
    FilterChip(
        selected = !isPaused,
        onClick = onToggle,
        label = { Text(label) },
        modifier =
            modifier
                .heightIn(min = MinTouchTarget)
                .focusOutline()
                .semantics { stateDescription = state },
    )
}
