package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.ui.icons.AppIcons
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.auto_refresh_active
import com.kotlinbitcoins.web.ui.resources.auto_refresh_paused
import com.kotlinbitcoins.web.ui.resources.auto_refresh_state_active
import com.kotlinbitcoins.web.ui.resources.auto_refresh_state_paused
import com.kotlinbitcoins.web.ui.theme.LocalExtendedColors
import org.jetbrains.compose.resources.stringResource

private val ToggleIconSize = 18.dp

/**
 * Pause/resume of the auto-refresh (FR-012), with the state in the visible label and in the semantics. A pill on
 * the shell; the icon shows what a click does (pause while active, resume while paused) and is decorative.
 */
@Composable
fun PauseToggle(
    isPaused: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(if (isPaused) Res.string.auto_refresh_paused else Res.string.auto_refresh_active)
    val state =
        stringResource(if (isPaused) Res.string.auto_refresh_state_paused else Res.string.auto_refresh_state_active)
    val colors = MaterialTheme.colorScheme
    val card = LocalExtendedColors.current.card
    FilterChip(
        selected = !isPaused,
        onClick = onToggle,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = {
            Icon(
                imageVector = if (isPaused) AppIcons.Play else AppIcons.Pause,
                contentDescription = null,
                modifier = Modifier.size(ToggleIconSize),
            )
        },
        shape = CircleShape,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = card,
                labelColor = colors.onSurface,
                iconColor = colors.onSurfaceVariant,
                selectedContainerColor = card,
                selectedLabelColor = colors.onSurface,
                selectedLeadingIconColor = colors.primary,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = !isPaused,
                borderColor = colors.outlineVariant,
                selectedBorderColor = colors.outlineVariant,
                borderWidth = 1.dp,
                selectedBorderWidth = 1.dp,
            ),
        modifier =
            modifier
                .heightIn(min = MinTouchTarget)
                .focusOutline()
                .semantics { stateDescription = state },
    )
}
