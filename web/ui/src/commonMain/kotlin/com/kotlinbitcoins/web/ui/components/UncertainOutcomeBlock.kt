package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.error_uncertain_balance_link
import com.kotlinbitcoins.web.ui.resources.error_uncertain_statement_link
import com.kotlinbitcoins.web.ui.shared.message
import org.jetbrains.compose.resources.stringResource

/**
 * Result of a financial operation whose response was lost after it was sent (FR-041, contracts/error-messages.md):
 * the message of [OperationError.Uncertain] in the alert region, focused and announced once, with shortcuts to the
 * balance (dashboard) and to the statement. There is **no** resend: the person checks before trying again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UncertainOutcomeBlock(
    onViewBalance: () -> Unit,
    onViewStatement: () -> Unit,
    announce: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = OperationError.Uncertain.message()
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusOnArrival(enabled = true) { arrivalModifier ->
                Text(text = message, style = MaterialTheme.typography.bodyLarge, modifier = arrivalModifier)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val buttonModifier = Modifier.heightIn(min = MinTouchTarget).focusOutline()
                SecondaryButton(onClick = onViewBalance, modifier = buttonModifier) {
                    Text(stringResource(Res.string.error_uncertain_balance_link))
                }
                SecondaryButton(onClick = onViewStatement, modifier = buttonModifier) {
                    Text(stringResource(Res.string.error_uncertain_statement_link))
                }
            }
        }
    }
    // stringResource is empty until the resources load: announce the real text, once.
    LaunchedEffect(message) { if (message.isNotBlank()) announce(message) }
}
