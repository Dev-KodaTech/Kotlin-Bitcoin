package com.kotlinbitcoins.web.ui.account

/** What the login page can do; implemented by the LoginViewModel and the navigation. */
class LoginActions(
    val onEmailChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onGoToRegister: () -> Unit,
    val announce: (String) -> Unit,
)
