package com.kotlinbitcoins.web.ui.components

import androidx.compose.ui.focus.FocusRequester

/** One invalid field of a form: its message and how to reach it. */
data class FormFieldError(
    val message: String,
    val focusRequester: FocusRequester,
)
