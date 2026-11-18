package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.ui.theme.LocalExtendedColors

/** Padding inside a card: smaller in a dense window (see [LocalDenseLayout]). */
val CardPadding: Dp
    @Composable get() = if (LocalDenseLayout.current) 16.dp else 24.dp

/** Space below the last block of a page; each screen adds it inside its own scroll. */
val PageBottomPadding = 20.dp

/**
 * True in a narrow or short window (a phone, or a desktop window at 200% zoom): the shell provides it and the
 * blocks use less padding, so the content of the page starts higher.
 */
val LocalDenseLayout = staticCompositionLocalOf { false }

private val CardShadowElevation = 16.dp

/**
 * The card of the "Azul suave" look: [color] (white, or the card tone of the dark theme) on the shell, large
 * corners and a soft blue shadow in the light theme. Same call shape as the Material 3 `Card`.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = LocalExtendedColors.current.card,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val shadow = LocalExtendedColors.current.cardShadow
    Surface(
        modifier =
            modifier.shadow(
                elevation = if (shadow.alpha > 0f) CardShadowElevation else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = shadow,
                spotColor = shadow,
            ),
        shape = shape,
        color = color,
        contentColor = contentColor,
    ) {
        Column(content = content)
    }
}

/** A card with the usual padding and, when [title] is given, its heading. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    color: Color = LocalExtendedColors.current.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(modifier = modifier.fillMaxWidth(), color = color) {
        val spacing = if (LocalDenseLayout.current) 8.dp else 12.dp
        Column(modifier = Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(spacing)) {
            if (title != null) SectionTitle(title)
            content()
        }
    }
}

/** Heading of a section of the page (level 2 in the mirror). */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}
