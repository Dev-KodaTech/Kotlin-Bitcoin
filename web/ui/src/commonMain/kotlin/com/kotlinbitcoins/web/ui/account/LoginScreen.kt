package com.kotlinbitcoins.web.ui.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.state.account.LoginUiState
import com.kotlinbitcoins.web.state.session.LoginBanner
import com.kotlinbitcoins.web.ui.components.FormTextField
import com.kotlinbitcoins.web.ui.components.PageHeading
import com.kotlinbitcoins.web.ui.components.SubmitButton
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.banner_account_created
import com.kotlinbitcoins.web.ui.resources.banner_session_expired
import com.kotlinbitcoins.web.ui.resources.error_invalid_credentials
import com.kotlinbitcoins.web.ui.resources.field_email
import com.kotlinbitcoins.web.ui.resources.field_password
import com.kotlinbitcoins.web.ui.resources.link_register
import com.kotlinbitcoins.web.ui.resources.login_no_account
import com.kotlinbitcoins.web.ui.resources.login_submit
import com.kotlinbitcoins.web.ui.resources.page_login
import com.kotlinbitcoins.web.ui.shared.message
import org.jetbrains.compose.resources.stringResource

/**
 * Login page (US1, FR-003): h1 "Entrar", the banner of the navigation or of the last attempt in the alert region
 * (focused and announced), e-mail and password, "Entrar" and the link "Criar conta". Stateless.
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    actions: LoginActions,
) {
    AccountPageLayout {
        PageHeading(stringResource(Res.string.page_login))

        state.banner?.let { banner ->
            val message = banner.text()
            key(state.feedbackId, banner) {
                AccountBanner(message = message, isError = banner.isError, announce = actions.announce)
            }
        }

        FormTextField(
            value = state.email,
            onValueChange = actions.onEmailChange,
            label = stringResource(Res.string.field_email),
            error = null,
            autofill = ContentType.Username,
            keyboardType = KeyboardType.Email,
        )
        FormTextField(
            value = state.password,
            onValueChange = actions.onPasswordChange,
            label = stringResource(Res.string.field_password),
            error = null,
            autofill = ContentType.Password,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            isPassword = true,
            onImeAction = actions.onSubmit,
        )
        SubmitButton(
            text = stringResource(Res.string.login_submit),
            isSubmitting = state.isSubmitting,
            onClick = actions.onSubmit,
            modifier = Modifier.fillMaxWidth(),
        )
        AccountSwitch(
            question = stringResource(Res.string.login_no_account),
            action = stringResource(Res.string.link_register),
            onClick = actions.onGoToRegister,
        )
    }
}

private val LoginBanner.isError: Boolean
    get() =
        when (this) {
            LoginBanner.SessionExpired, LoginBanner.AccountCreated -> false
            LoginBanner.InvalidCredentials, is LoginBanner.Error -> true
        }

@Composable
private fun LoginBanner.text(): String =
    when (this) {
        LoginBanner.SessionExpired -> stringResource(Res.string.banner_session_expired)
        LoginBanner.AccountCreated -> stringResource(Res.string.banner_account_created)
        LoginBanner.InvalidCredentials -> stringResource(Res.string.error_invalid_credentials)
        is LoginBanner.Error -> error.loginMessage()
    }

/** A VALIDATION_ERROR of the login lists the API messages after the summary (e.g. an empty e-mail). */
@Composable
private fun ApiError.loginMessage(): String =
    when (this) {
        is ApiError.Validation -> (listOf(message()) + fieldErrors.map { it.message }).joinToString(" ")

        ApiError.InvalidAmount,
        ApiError.EmailAlreadyInUse,
        ApiError.InvalidCredentials,
        ApiError.Unauthorized,
        ApiError.InsufficientBalance,
        ApiError.RedemptionExceedsAvailable,
        ApiError.InvalidDateRange,
        ApiError.QuoteUnavailable,
        ApiError.ServiceUnavailable,
        ApiError.Unexpected,
        ApiError.NoConnection,
        -> message()
    }
