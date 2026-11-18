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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.ui.theme.focusColor

private val FocusOutlineWidth = 3.dp

/**
 * Visible focus drawn on the canvas, 3 dp in `primary` (A2 of W-00; contrast checked by ThemeContrastTest). On a
 * filled block (the hero, the current menu item) pass a [color] that contrasts with that block instead.
 */
fun Modifier.focusOutline(color: Color = Color.Unspecified): Modifier =
    composed {
        var isFocused by remember { mutableStateOf(false) }
        val ring = if (color.isSpecified) color else focusColor(MaterialTheme.colorScheme)
        val shape = MaterialTheme.shapes.small
        this
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (isFocused) Modifier.border(FocusOutlineWidth, ring, shape) else Modifier)
    }
