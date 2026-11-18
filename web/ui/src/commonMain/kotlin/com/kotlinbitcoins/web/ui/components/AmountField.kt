package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.currency_prefix
import org.jetbrains.compose.resources.stringResource

/**
 * Amount in BRL (FR-039, W-11): visible label, "R$" prefix, decimal keyboard, error in the semantics and below the
 * field. No mask that moves the cursor. [focusRequester] lets the screen move the focus to the field on an error.
 */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    enabled: Boolean = true,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        prefix = { Text(stringResource(Res.string.currency_prefix)) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = fieldColors(),
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier =
            modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics { if (error != null) error(error) }
                .accessibleField(label, error),
    )
}
