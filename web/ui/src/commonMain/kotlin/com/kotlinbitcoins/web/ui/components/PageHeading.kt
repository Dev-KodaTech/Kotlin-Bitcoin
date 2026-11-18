package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/**
 * Page heading: the h1 of the screen (A8; the shim of :app gives level 1 to the first heading). With
 * [focusOnArrival], it takes the focus when the page opens (FR-046) and then leaves the Tab order, like an HTML
 * heading with `tabindex="-1"`.
 */
@Composable
fun PageHeading(
    text: String,
    modifier: Modifier = Modifier,
    focusOnArrival: Boolean = true,
) {
    val typography = MaterialTheme.typography
    val style = if (LocalDenseLayout.current) typography.headlineSmall else typography.headlineMedium
    FocusOnArrival(focusOnArrival) { arrivalModifier ->
        Text(
            text = text,
            style = style,
            modifier = modifier.semantics { heading() }.then(arrivalModifier),
        )
    }
}

/** Gives [content] a modifier that is focusable only until the first focus it receives on arrival. */
@Composable
internal fun FocusOnArrival(
    enabled: Boolean,
    content: @Composable (Modifier) -> Unit,
) {
    val requester = remember { FocusRequester() }
    var canFocus by remember { mutableStateOf(enabled) }
    var hadFocus by remember { mutableStateOf(false) }
    content(
        Modifier
            .focusProperties { this.canFocus = canFocus }
            .focusRequester(requester)
            .onFocusChanged {
                if (it.isFocused) hadFocus = true
                if (!it.isFocused && hadFocus) canFocus = false
            }.focusable(),
    )
    LaunchedEffect(Unit) {
        if (enabled) requester.requestFocus()
    }
}
