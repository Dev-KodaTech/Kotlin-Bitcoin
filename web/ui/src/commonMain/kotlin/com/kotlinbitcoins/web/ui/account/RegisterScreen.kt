package com.kotlinbitcoins.web.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.kotlinbitcoins.web.core.format.RegistrationError
import com.kotlinbitcoins.web.core.format.RegistrationField
import com.kotlinbitcoins.web.state.account.RegisterFieldError
import com.kotlinbitcoins.web.state.account.RegisterUiState
import com.kotlinbitcoins.web.ui.components.ErrorBlock
import com.kotlinbitcoins.web.ui.components.FormErrorSummary
import com.kotlinbitcoins.web.ui.components.FormFieldError
import com.kotlinbitcoins.web.ui.components.FormTextField
import com.kotlinbitcoins.web.ui.components.MinTouchTarget
import com.kotlinbitcoins.web.ui.components.PageHeading
import com.kotlinbitcoins.web.ui.components.SubmitButton
import com.kotlinbitcoins.web.ui.components.focusOutline
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.error_email_in_use
import com.kotlinbitcoins.web.ui.resources.error_email_invalid
import com.kotlinbitcoins.web.ui.resources.error_name_required
import com.kotlinbitcoins.web.ui.resources.error_name_too_long
import com.kotlinbitcoins.web.ui.resources.error_password_too_long
import com.kotlinbitcoins.web.ui.resources.error_password_too_short
import com.kotlinbitcoins.web.ui.resources.field_email
import com.kotlinbitcoins.web.ui.resources.field_name
import com.kotlinbitcoins.web.ui.resources.field_password
import com.kotlinbitcoins.web.ui.resources.link_login
import com.kotlinbitcoins.web.ui.resources.page_register
import com.kotlinbitcoins.web.ui.resources.password_hint
import com.kotlinbitcoins.web.ui.resources.register_has_account
import com.kotlinbitcoins.web.ui.resources.register_submit
import com.kotlinbitcoins.web.ui.shared.message
import org.jetbrains.compose.resources.stringResource

/**
 * Registration page (US1, FR-001): h1 "Criar conta", labelled fields with autofill, one error per field, summary of
 * the errors, focus on the first invalid field and, on a duplicate e-mail, a shortcut to the login. Stateless.
 */
@Composable
fun RegisterScreen(
    state: RegisterUiState,
    actions: RegisterActions,
) {
    val requesters = remember { RegistrationField.entries.associateWith { FocusRequester() } }
    val messages = RegistrationField.entries.associateWith { state.fieldErrors[it]?.text() }

    LaunchedEffect(state.focusRequest, state.feedbackId) {
        val field = state.focusRequest ?: return@LaunchedEffect
        requesters.getValue(field).requestFocus()
        actions.onFocusHandled()
    }

    AccountPageLayout {
        PageHeading(stringResource(Res.string.page_register))

        state.formError?.let { error ->
            val message = error.message()
            key(state.feedbackId) {
                ErrorBlock(message = message, focusOnShow = true)
                LaunchedEffect(message) { if (message.isNotBlank()) actions.announce(message) }
            }
        }
        key(state.feedbackId) {
            FormErrorSummary(
                errors =
                    RegistrationField.entries.mapNotNull { field ->
                        messages[field]?.let { FormFieldError(it, requesters.getValue(field)) }
                    },
                otherMessages = state.otherFieldMessages,
                // With a field to focus, the focus goes there; otherwise to the summary.
                focusOnShow = state.focusRequest == null && state.fieldErrors.isEmpty(),
            )
        }

        RegisterFields(state, actions, requesters, messages)

        SubmitButton(
            text = stringResource(Res.string.register_submit),
            isSubmitting = state.isSubmitting,
            onClick = actions.onSubmit,
            modifier = Modifier.fillMaxWidth(),
        )
        AccountSwitch(
            question = stringResource(Res.string.register_has_account),
            action = stringResource(Res.string.link_login),
            onClick = actions.onGoToLogin,
        )
    }
}

@Composable
private fun RegisterFields(
    state: RegisterUiState,
    actions: RegisterActions,
    requesters: Map<RegistrationField, FocusRequester>,
    messages: Map<RegistrationField, String?>,
) {
    FormTextField(
        value = state.name,
        onValueChange = actions.onNameChange,
        label = stringResource(Res.string.field_name),
        error = messages[RegistrationField.Name],
        autofill = ContentType.PersonFullName,
        focusRequester = requesters.getValue(RegistrationField.Name),
    )
    Column {
        FormTextField(
            value = state.email,
            onValueChange = actions.onEmailChange,
            label = stringResource(Res.string.field_email),
            error = messages[RegistrationField.Email],
            autofill = ContentType.EmailAddress,
            keyboardType = KeyboardType.Email,
            focusRequester = requesters.getValue(RegistrationField.Email),
        )
        if (state.fieldErrors[RegistrationField.Email] == RegisterFieldError.EmailAlreadyInUse) {
            TextButton(
                onClick = actions.onGoToLogin,
                modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline(),
            ) {
                Text(stringResource(Res.string.link_login))
            }
        }
    }
    FormTextField(
        value = state.password,
        onValueChange = actions.onPasswordChange,
        label = stringResource(Res.string.field_password),
        error = messages[RegistrationField.Password],
        autofill = ContentType.NewPassword,
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
        isPassword = true,
        hint = stringResource(Res.string.password_hint),
        focusRequester = requesters.getValue(RegistrationField.Password),
        onImeAction = actions.onSubmit,
    )
}

@Composable
private fun RegisterFieldError.text(): String =
    when (this) {
        is RegisterFieldError.Local -> stringResource(error.resource())
        is RegisterFieldError.Server -> message
        RegisterFieldError.EmailAlreadyInUse -> stringResource(Res.string.error_email_in_use)
    }

private fun RegistrationError.resource() =
    when (this) {
        RegistrationError.NameBlank -> Res.string.error_name_required
        RegistrationError.NameTooLong -> Res.string.error_name_too_long
        RegistrationError.EmailInvalid -> Res.string.error_email_invalid
        RegistrationError.PasswordTooShort -> Res.string.error_password_too_short
        RegistrationError.PasswordTooLong -> Res.string.error_password_too_long
    }
