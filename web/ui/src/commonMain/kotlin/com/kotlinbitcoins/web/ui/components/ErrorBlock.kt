package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.retry
import org.jetbrains.compose.resources.stringResource

/**
 * Error of a block or screen: the pt-BR message only (never a technical detail, SC-003) and, when the error is
 * retryable, "Tentar de novo". With [focusOnShow], the message takes the focus (FR-046).
 */
@Composable
fun ErrorBlock(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    focusOnShow: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusOnArrival(focusOnShow) { arrivalModifier ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = arrivalModifier,
            )
        }
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline()) {
                Text(stringResource(Res.string.retry))
            }
        }
    }
}

/** Minimum target size for touch (WCAG 2.5.5 AAA used as house rule, M3 guideline). */
val MinTouchTarget = 48.dp
