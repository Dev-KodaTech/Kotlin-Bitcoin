package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.ui.theme.focusColor

private val FocusOutlineWidth = 3.dp

/** Visible focus drawn on the canvas, 3 dp in `primary` (A2 of W-00; contrast checked by ThemeContrastTest). */
fun Modifier.focusOutline(): Modifier =
    composed {
        var isFocused by remember { mutableStateOf(false) }
        val color = focusColor(MaterialTheme.colorScheme)
        val shape = MaterialTheme.shapes.small
        this
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (isFocused) Modifier.border(FocusOutlineWidth, color, shape) else Modifier)
    }
