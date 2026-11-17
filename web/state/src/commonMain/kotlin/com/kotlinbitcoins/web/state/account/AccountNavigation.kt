package com.kotlinbitcoins.web.state.account

import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.session.LoginBanner

/**
 * Where the registration or login page goes after a submit. It always **replaces** the current history entry, so
 * "back" never returns to the form (contracts/ui-routes.md, "Histórico do navegador").
 */
data class AccountNavigation(
    val route: AppRoute,
    val banner: LoginBanner?,
)
