package com.kotlinbitcoins.web.ui.components

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Mitigation M2 of W-00: the focused DOM element of a text field is a hidden input without name or error, so the
 * label and the error of the focused field are copied to it. The browser side lives in :app (it touches internals
 * of the CMP mirror); :ui only reports which field has the focus.
 */
fun interface FieldAccessibility {
    fun onFieldFocused(
        label: String,
        error: String?,
    )
}

val LocalFieldAccessibility = staticCompositionLocalOf { FieldAccessibility { _, _ -> } }

/** M1 (name of the field in the mirror) + M2 (name and error on the focused input). Use on every text field. */
fun Modifier.accessibleField(
    label: String,
    error: String?,
): Modifier =
    composed {
        val bridge = LocalFieldAccessibility.current
        var isFocused by remember { mutableStateOf(false) }
        LaunchedEffect(isFocused, error) {
            if (isFocused) bridge.onFieldFocused(label, error)
        }
        this
            .semantics { contentDescription = label }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) bridge.onFieldFocused(label, error)
            }
    }
