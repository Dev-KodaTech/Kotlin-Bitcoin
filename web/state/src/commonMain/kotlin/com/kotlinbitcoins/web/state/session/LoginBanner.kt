package com.kotlinbitcoins.web.state.session

import com.kotlinbitcoins.web.core.error.ApiError

/**
 * Messages of the login page, in its alert region (contracts/ui-routes.md, contracts/error-messages.md). The first
 * two come from the navigation; the others from a failed login.
 */
sealed interface LoginBanner {
    /** "Sua sessão expirou. Entre novamente para continuar." */
    data object SessionExpired : LoginBanner

    /** "Conta criada. Entre com seu e-mail e senha." */
    data object AccountCreated : LoginBanner

    /** "E-mail ou senha incorretos.": the same for an unknown e-mail and a wrong password (FR-003). */
    data object InvalidCredentials : LoginBanner

    /** Any other failure of the login (no connection, service unavailable, validation…). */
    data class Error(
        val error: ApiError,
    ) : LoginBanner
}
