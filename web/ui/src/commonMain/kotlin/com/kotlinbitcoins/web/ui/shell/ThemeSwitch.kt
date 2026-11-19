package com.kotlinbitcoins.web.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.state.port.ThemeMode
import com.kotlinbitcoins.web.ui.components.MinTouchTarget
import com.kotlinbitcoins.web.ui.components.focusOutline
import com.kotlinbitcoins.web.ui.icons.AppIcons
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.theme_current
import com.kotlinbitcoins.web.ui.resources.theme_dark
import com.kotlinbitcoins.web.ui.resources.theme_label
import com.kotlinbitcoins.web.ui.resources.theme_light
import com.kotlinbitcoins.web.ui.resources.theme_system
import com.kotlinbitcoins.web.ui.theme.LocalExtendedColors
import com.kotlinbitcoins.web.ui.theme.LocalThemeChoice
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The theme switch of the shell. Always in place, never in a popup: a closed popup (DropdownMenu) leaves the
 * accessibility mirror of Compose 1.12.2 showing its old nodes, so the options live in the page itself.
 */

private val SegmentIconSize = 18.dp
private val RailOptionWidth = 56.dp

/** Width of a NavigationRailItem: the options line up with the destinations above them. */
private val RailItemWidth = 80.dp

private class ThemeOption(
    val mode: ThemeMode,
    val label: StringResource,
    val icon: ImageVector,
)

private val themeOptions =
    listOf(
        ThemeOption(ThemeMode.System, Res.string.theme_system, AppIcons.ThemeSystem),
        ThemeOption(ThemeMode.Light, Res.string.theme_light, AppIcons.ThemeLight),
        ThemeOption(ThemeMode.Dark, Res.string.theme_dark, AppIcons.ThemeDark),
    )

/**
 * Sidebar and compact menu: "Tema" and the three options side by side, the current one as a filled pill like the
 * current page. Kept short (no line of explanation) so that the destinations still fit a 800 px high window.
 */
@Composable
internal fun ThemeSegmentedSwitch(modifier: Modifier = Modifier) {
    val choice = LocalThemeChoice.current
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(Res.string.theme_label),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(LocalExtendedColors.current.tint, MaterialTheme.shapes.medium)
                    .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            themeOptions.forEach { option ->
                val label = stringResource(option.label)
                ThemeOptionButton(
                    selected = option.mode == choice.mode,
                    onClick = { choice.select(option.mode) },
                    modifier = Modifier.weight(1f).currentTheme(label, option.mode == choice.mode),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    ) {
                        Icon(option.icon, contentDescription = null, modifier = Modifier.size(SegmentIconSize))
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Navigation rail (too narrow for the row): "Tema" and the three options as icons, one under the other. */
@Composable
internal fun ThemeRailSwitch(modifier: Modifier = Modifier) {
    val choice = LocalThemeChoice.current
    Column(
        modifier = modifier.width(RailItemWidth).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(Res.string.theme_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        themeOptions.forEach { option ->
            val label = stringResource(option.label)
            val selected = option.mode == choice.mode
            ThemeOptionButton(
                selected = selected,
                onClick = { choice.select(option.mode) },
                modifier =
                    Modifier
                        .size(
                            RailOptionWidth,
                            MinTouchTarget,
                        ).currentTheme(label, selected, iconOnly = true),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(option.icon, contentDescription = null)
                }
            }
        }
    }
}

/** One option: filled with the hero color when [selected], as the current page in the navigation. */
@Composable
private fun ThemeOptionButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val extended = LocalExtendedColors.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) extended.hero else Color.Transparent,
        contentColor = if (selected) extended.onHero else scheme.onSurfaceVariant,
        modifier =
            modifier
                .heightIn(min = MinTouchTarget)
                .focusOutline(if (selected) scheme.onSurface else Color.Unspecified),
        content = content,
    )
}

/**
 * L6 of W-00, as the current page: the mirror drops `selected`, so the current theme is also part of the name. An
 * option without visible text ([iconOnly]) is named by its label.
 */
@Composable
private fun Modifier.currentTheme(
    label: String,
    isCurrent: Boolean,
    iconOnly: Boolean = false,
): Modifier {
    val currentName = stringResource(Res.string.theme_current, label)
    return semantics {
        selected = isCurrent
        if (isCurrent) {
            contentDescription = currentName
        } else if (iconOnly) {
            contentDescription = label
        }
    }
}
