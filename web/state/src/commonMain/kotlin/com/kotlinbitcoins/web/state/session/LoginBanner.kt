package com.kotlinbitcoins.web.state.session

/** Messages shown on the login page because of the navigation (contracts/ui-routes.md). */
sealed interface LoginBanner {
    /** "Sua sessão expirou. Entre novamente para continuar." */
    data object SessionExpired : LoginBanner

    /** "Conta criada. Entre com seu e-mail e senha." */
    data object AccountCreated : LoginBanner
}
