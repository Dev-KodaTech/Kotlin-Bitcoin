package com.kotlinbitcoins.web.state.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.format.RegistrationError
import com.kotlinbitcoins.web.core.format.RegistrationField
import com.kotlinbitcoins.web.core.format.RegistrationValidator
import com.kotlinbitcoins.web.core.format.ValidRegistration
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.port.AuthGateway
import com.kotlinbitcoins.web.state.session.LoginBanner
import com.kotlinbitcoins.web.state.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why a registration field shows an error. */
sealed interface RegisterFieldError {
    /** Refused before sending (FR-001). */
    data class Local(
        val error: RegistrationError,
    ) : RegisterFieldError

    /** VALIDATION_ERROR of the API: `errors[].message`, already in pt-BR. */
    data class Server(
        val message: String,
    ) : RegisterFieldError

    /** EMAIL_ALREADY_IN_USE: shown on the e-mail field with a shortcut to the login (US1-3). */
    data object EmailAlreadyInUse : RegisterFieldError
}

/** State of the registration page (data-model §4.4). The password never appears in `toString()`. */
data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val fieldErrors: Map<RegistrationField, RegisterFieldError> = emptyMap(),
    /** VALIDATION_ERROR messages of fields that are not in the form: shown in the summary. */
    val otherFieldMessages: List<String> = emptyList(),
    /** An error of the whole form (no connection, service unavailable…). */
    val formError: ApiError? = null,
    val isSubmitting: Boolean = false,
    /** One-shot: the field that must take the focus; the page calls `onFocusHandled` after moving it. */
    val focusRequest: RegistrationField? = null,
    /** One-shot: where to go; the page calls `onNavigationHandled` after navigating. */
    val navigation: AccountNavigation? = null,
    /** Changes on every submit result, so the page focuses and announces a repeated error again. */
    val feedbackId: Int = 0,
) {
    override fun toString(): String =
        "RegisterUiState(name=$name, email=$email, password=****, fieldErrors=$fieldErrors, " +
            "otherFieldMessages=$otherFieldMessages, formError=$formError, isSubmitting=$isSubmitting, " +
            "focusRequest=$focusRequest, navigation=$navigation, feedbackId=$feedbackId)"
}

/**
 * Registration (US1, FR-001/FR-002): validates locally, creates the account and logs in with the same credentials.
 * If the account was created but the automatic login failed, the login page opens with "Conta criada…".
 */
class RegisterViewModel(
    private val authGateway: AuthGateway,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RegisterUiState())

    val uiState: StateFlow<RegisterUiState> = mutableState.asStateFlow()

    fun onNameChange(value: String) = edit(RegistrationField.Name) { copy(name = value) }

    fun onEmailChange(value: String) = edit(RegistrationField.Email) { copy(email = value) }

    fun onPasswordChange(value: String) = edit(RegistrationField.Password) { copy(password = value) }

    fun onFocusHandled() = mutableState.update { it.copy(focusRequest = null) }

    fun onNavigationHandled() = mutableState.update { it.copy(navigation = null) }

    fun onSubmit() {
        val current = mutableState.value
        if (current.isSubmitting || current.navigation != null) return
        when (val validated = RegistrationValidator.validate(current.name, current.email, current.password)) {
            is Outcome.Failure -> {
                val errors = validated.error.associate { it.field to RegisterFieldError.Local(it) }
                showFeedback {
                    copy(
                        fieldErrors = errors,
                        otherFieldMessages = emptyList(),
                        formError = null,
                        focusRequest = validated.error.first().field,
                    )
                }
            }

            is Outcome.Success -> {
                mutableState.update {
                    it.copy(
                        isSubmitting = true,
                        fieldErrors = emptyMap(),
                        otherFieldMessages = emptyList(),
                        formError = null,
                    )
                }
                viewModelScope.launch { submit(validated.value) }
            }
        }
    }

    private suspend fun submit(input: ValidRegistration) {
        when (val created = authGateway.register(input.name, input.email, input.password)) {
            is Outcome.Failure -> onRegisterFailed(created.error)
            is Outcome.Success -> logIn(input)
        }
    }

    private suspend fun logIn(input: ValidRegistration) {
        val navigation =
            when (val login = authGateway.login(input.email, input.password)) {
                is Outcome.Success -> {
                    sessionManager.onLoggedIn(login.value.accessToken, login.value.expiresIn)
                    AccountNavigation(AppRoute.Dashboard, banner = null)
                }

                is Outcome.Failure -> {
                    AccountNavigation(AppRoute.Login(), LoginBanner.AccountCreated)
                }
            }
        // Still "submitting" while the page leaves, so the button does not come back for a moment.
        mutableState.update { it.copy(navigation = navigation) }
    }

    private fun onRegisterFailed(error: ApiError) {
        when (error) {
            ApiError.EmailAlreadyInUse -> {
                showFeedback {
                    copy(
                        password = "",
                        fieldErrors = mapOf(RegistrationField.Email to RegisterFieldError.EmailAlreadyInUse),
                        focusRequest = RegistrationField.Email,
                    )
                }
            }

            is ApiError.Validation -> {
                val byField = error.fieldErrors.groupBy { FIELD_NAMES[it.field] }
                val fieldErrors =
                    RegistrationField.entries
                        .mapNotNull { field -> byField[field]?.first()?.let { field to it.message } }
                        .associate { (field, message) -> field to RegisterFieldError.Server(message) }
                showFeedback {
                    copy(
                        fieldErrors = fieldErrors,
                        otherFieldMessages = byField[null].orEmpty().map { it.message },
                        focusRequest = fieldErrors.keys.firstOrNull(),
                    )
                }
            }

            ApiError.InvalidAmount,
            ApiError.InvalidCredentials,
            ApiError.Unauthorized,
            ApiError.InsufficientBalance,
            ApiError.RedemptionExceedsAvailable,
            ApiError.InvalidDateRange,
            ApiError.QuoteUnavailable,
            ApiError.ServiceUnavailable,
            ApiError.Unexpected,
            ApiError.NoConnection,
            -> {
                showFeedback { copy(formError = error) }
            }
        }
    }

    private fun edit(
        field: RegistrationField,
        change: RegisterUiState.() -> RegisterUiState,
    ) {
        mutableState.update { state ->
            if (state.isSubmitting) state else state.change().copy(fieldErrors = state.fieldErrors - field)
        }
    }

    private fun showFeedback(change: RegisterUiState.() -> RegisterUiState) {
        mutableState.update { it.change().copy(isSubmitting = false, feedbackId = it.feedbackId + 1) }
    }

    private companion object {
        /** `field` of the API's VALIDATION_ERROR → field of the form (RegisterAccountRequest). */
        val FIELD_NAMES =
            mapOf(
                "name" to RegistrationField.Name,
                "email" to RegistrationField.Email,
                "password" to RegistrationField.Password,
            )
    }
}
