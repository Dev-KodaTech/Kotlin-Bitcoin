package com.kotlinbitcoins.web.ui.shell

import com.kotlinbitcoins.web.state.account.LoginViewModel
import com.kotlinbitcoins.web.state.account.RegisterViewModel
import com.kotlinbitcoins.web.state.session.LoginBanner

/**
 * Factories of the ViewModels of each destination, built by `AppContainer` in :app (the only place that knows the
 * gateways of :data). Each ViewModel lives as long as its back stack entry. Each user story adds its factory.
 */
class AppViewModels(
    val register: () -> RegisterViewModel,
    val login: (returnTo: String?, banner: LoginBanner?) -> LoginViewModel,
)
