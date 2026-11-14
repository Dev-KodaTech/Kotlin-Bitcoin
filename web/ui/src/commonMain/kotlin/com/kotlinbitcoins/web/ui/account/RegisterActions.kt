package com.kotlinbitcoins.web.ui.account

/** What the registration page can do; implemented by the RegisterViewModel and the navigation. */
class RegisterActions(
    val onNameChange: (String) -> Unit,
    val onEmailChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onFocusHandled: () -> Unit,
    val onGoToLogin: () -> Unit,
    val announce: (String) -> Unit,
)
