package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.processing
import org.jetbrains.compose.resources.stringResource

/**
 * Submit with single flight (FR-043): while [isSubmitting] the button is disabled, reads "Processando…" and ignores
 * click, tap and Enter. The state machine of :state already ignores repeated intents; this is the visible half.
 */
@Composable
fun SubmitButton(
    text: String,
    isSubmitting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryButton(
        onClick = { if (!isSubmitting) onClick() },
        enabled = !isSubmitting,
        modifier = modifier.heightIn(min = MinTouchTarget).focusOutline(),
    ) {
        Text(if (isSubmitting) stringResource(Res.string.processing) else text)
    }
}
