package com.kotlinbitcoins.web.spike

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private const val MIN_PASSWORD_LENGTH = 8
private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private val AMOUNT = Regex("^\\d{1,3}(\\.\\d{3})*(,\\d{1,2})?$|^\\d+(,\\d{1,2})?$")

private class FieldState(
    val label: String,
    val keyboardType: KeyboardType,
    val validate: (String) -> String?,
) {
    var value by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    val focusRequester = FocusRequester()
}

/**
 * Registration form (T017): one error per field (semantics error + text below the field). An invalid submit moves
 * the focus to the first invalid field (A5). The amount field asks for the decimal keyboard (A11).
 */
@Composable
fun RegisterFormSpike() {
    val fields =
        remember {
            listOf(
                FieldState("Nome", KeyboardType.Text) { if (it.isBlank()) "Informe o nome." else null },
                FieldState("E-mail", KeyboardType.Email) {
                    if (EMAIL.matches(it.trim())) null else "Informe um e-mail válido, como nome@exemplo.com."
                },
                FieldState("Senha", KeyboardType.Password) {
                    if (it.length >= MIN_PASSWORD_LENGTH) null else "A senha precisa ter pelo menos 8 caracteres."
                },
                FieldState("Valor do primeiro depósito (R$)", KeyboardType.Decimal) {
                    if (AMOUNT.matches(it.trim())) null else "Informe um valor como 1.234,56."
                },
            )
        }
    var sent by remember { mutableStateOf(false) }

    val submit = {
        fields.forEach { it.error = it.validate(it.value) }
        val firstInvalid = fields.firstOrNull { it.error != null }
        sent = firstInvalid == null
        firstInvalid?.focusRequester?.requestFocus()
        Unit
    }

    ContentColumn(modifier = Modifier.verticalScroll(rememberScrollState())) {
        PageHeading("Criar conta")
        fields.forEachIndexed { index, field ->
            Spacer(Modifier.height(8.dp))
            SpikeTextField(field, isLast = index == fields.lastIndex)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = submit, modifier = Modifier.fillMaxWidth().focusOutline()) {
            Text("Cadastrar")
        }
        if (sent) {
            Spacer(Modifier.height(8.dp))
            Text("Dados válidos.")
        }
    }
}

@Composable
private fun SpikeTextField(
    field: FieldState,
    isLast: Boolean,
) {
    val error = field.error
    OutlinedTextField(
        value = field.value,
        onValueChange = {
            field.value = it
            field.error = null
        },
        label = { Text(field.label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation =
            if (field.keyboardType == KeyboardType.Password) {
                PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
        keyboardOptions =
            KeyboardOptions(
                keyboardType = field.keyboardType,
                imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(field.focusRequester)
                .semantics { if (error != null) error(error) }
                .accessibleField(field.label, error),
    )
}
