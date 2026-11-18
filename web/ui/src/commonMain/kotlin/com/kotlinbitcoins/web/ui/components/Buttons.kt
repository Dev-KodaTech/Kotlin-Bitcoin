package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Narrower sides in a dense window, so two actions fit side by side on a 320 px phone. */
private val ButtonPadding: PaddingValues
    @Composable get() = PaddingValues(horizontal = if (LocalDenseLayout.current) 14.dp else 22.dp, vertical = 12.dp)

/** The filled action of a block, with the corners of the "Azul suave" look. Same call shape as `Button`. */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = ButtonPadding,
        content = content,
    )
}

/** The secondary action next to a [PrimaryButton]. Same call shape as `OutlinedButton`. */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (enabled) colors.outline else colors.outlineVariant),
        contentPadding = ButtonPadding,
        content = content,
    )
}
