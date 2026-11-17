package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.error_validation_summary
import org.jetbrains.compose.resources.stringResource

/**
 * "Revise os campos destacados." with one item per error; each item moves the focus to its field. [otherMessages]
 * are errors of the API for fields that are not in the form: listed as text.
 */
@Composable
fun FormErrorSummary(
    errors: List<FormFieldError>,
    modifier: Modifier = Modifier,
    focusOnShow: Boolean = false,
    otherMessages: List<String> = emptyList(),
) {
    if (errors.isEmpty() && otherMessages.isEmpty()) return
    Column(modifier = modifier) {
        FocusOnArrival(focusOnShow) { arrivalModifier ->
            Text(
                text = stringResource(Res.string.error_validation_summary),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
                modifier = arrivalModifier,
            )
        }
        errors.forEach { error ->
            TextButton(
                onClick = { error.focusRequester.requestFocus() },
                modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline(),
            ) {
                Text(error.message)
            }
        }
        otherMessages.forEach { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
