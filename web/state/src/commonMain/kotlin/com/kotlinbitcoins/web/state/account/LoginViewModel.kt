package com.kotlinbitcoins.web.state.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.navigation.ReturnDestination
import com.kotlinbitcoins.web.state.port.AuthGateway
import com.kotlinbitcoins.web.state.session.LoginBanner
import com.kotlinbitcoins.web.state.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the login page (data-model §4.4). The password never appears in `toString()`. */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val banner: LoginBanner? = null,
    val isSubmitting: Boolean = false,
    /** One-shot: where to go after login; the page calls `onNavigationHandled` after navigating. */
    val navigation: AccountNavigation? = null,
    /** Changes on every submit result, so the page focuses and announces a repeated banner again. */
    val feedbackId: Int = 0,
) {
    override fun toString(): String =
        "LoginUiState(email=$email, password=****, banner=$banner, isSubmitting=$isSubmitting, " +
            "navigation=$navigation, feedbackId=$feedbackId)"
}

/**
 * Login (US1, FR-003/FR-007): the same message for an unknown e-mail and a wrong password, and after login the
 * destination of [returnTo] when it is a protected route of the app ([ReturnDestination]); otherwise the dashboard.
 */
class LoginViewModel(
    private val authGateway: AuthGateway,
    private val sessionManager: SessionManager,
    private val returnTo: String?,
    initialBanner: LoginBanner?,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState(banner = initialBanner))

    val uiState: StateFlow<LoginUiState> = mutableState.asStateFlow()

    fun onEmailChange(value: String) = edit { copy(email = value) }

    fun onPasswordChange(value: String) = edit { copy(password = value) }

    fun onNavigationHandled() = mutableState.update { it.copy(navigation = null) }

    fun onSubmit() {
        val current = mutableState.value
        if (current.isSubmitting || current.navigation != null) return
        mutableState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            when (val login = authGateway.login(current.email.trim(), current.password)) {
                is Outcome.Success -> {
                    sessionManager.onLoggedIn(login.value.accessToken, login.value.expiresIn)
                    val destination = AccountNavigation(ReturnDestination.resolve(returnTo), banner = null)
                    mutableState.update { it.copy(navigation = destination) }
                }

                is Outcome.Failure -> {
                    onFailed(login.error)
                }
            }
        }
    }

    private fun onFailed(error: ApiError) {
        val invalidCredentials = error == ApiError.InvalidCredentials
        val banner = if (invalidCredentials) LoginBanner.InvalidCredentials else LoginBanner.Error(error)
        mutableState.update {
            it.copy(
                banner = banner,
                // A wrong password is not kept; after a network failure the input is still valid.
                password = if (invalidCredentials) "" else it.password,
                isSubmitting = false,
                feedbackId = it.feedbackId + 1,
            )
        }
    }

    private fun edit(change: LoginUiState.() -> LoginUiState) {
        mutableState.update { if (it.isSubmitting) it else it.change() }
    }
}
