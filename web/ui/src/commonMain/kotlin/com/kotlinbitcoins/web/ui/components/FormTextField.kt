package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.kotlinbitcoins.web.ui.theme.LocalExtendedColors

/**
 * Text field of a form (FR-046): visible label that is also its accessible name (M1/M2 of W-00), error below the
 * field and in the semantics, autofill hint and the keyboard of [keyboardType]. [errorAction] is shown below the
 * error (e.g. the "Entrar" shortcut of a duplicate e-mail).
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    /** Null for fields with no autofill meaning (e.g. the dates of the statement filter). */
    autofill: ContentType?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isPassword: Boolean = false,
    hint: String? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onImeAction: () -> Unit = {},
) {
    val supporting = error ?: hint
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = fieldColors(),
        enabled = enabled,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
                autoCorrectEnabled = false,
            ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        modifier =
            modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics {
                    if (autofill != null) contentType = autofill
                    if (error != null) error(error)
                }.accessibleField(label, error),
    )
}

/** Fields are filled with the card color, so they read as fields on the shell and inside tinted blocks. */
@Composable
internal fun fieldColors(): TextFieldColors {
    val card = LocalExtendedColors.current.card
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = card,
        unfocusedContainerColor = card,
        errorContainerColor = card,
        disabledContainerColor = card,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    )
}
