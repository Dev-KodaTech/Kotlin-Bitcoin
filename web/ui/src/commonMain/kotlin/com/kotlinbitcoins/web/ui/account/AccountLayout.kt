package com.kotlinbitcoins.web.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.ui.components.AppCard
import com.kotlinbitcoins.web.ui.components.FocusOnArrival
import com.kotlinbitcoins.web.ui.components.MinTouchTarget
import com.kotlinbitcoins.web.ui.components.focusOutline
import com.kotlinbitcoins.web.ui.icons.AppIcons
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.app_name
import com.kotlinbitcoins.web.ui.theme.LocalExtendedColors
import org.jetbrains.compose.resources.stringResource

/**
 * Public pages (login and registration): the brand and one narrow card on the tinted ground, centered, that scrolls
 * vertically only. The brand is plain text, so the h1 of the page stays the first heading.
 */
@Composable
internal fun AccountPageLayout(content: @Composable ColumnScope.() -> Unit) {
    val extended = LocalExtendedColors.current
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(
                        max = MaxFormWidth,
                    ).fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(BrandMarkSize).background(extended.hero, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Bitcoin, contentDescription = null, tint = extended.onHero)
                }
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleMedium)
            }
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}

/**
 * Message of the page in the alert region (contracts/ui-routes.md): it takes the focus when it appears and is
 * written to the assertive live region once ([announce]). The caller recreates it (`key`) to show it again.
 */
@Composable
internal fun AccountBanner(
    message: String,
    isError: Boolean,
    announce: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (isError) colors.errorContainer else colors.primaryContainer,
        contentColor = if (isError) colors.onErrorContainer else colors.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        FocusOnArrival(enabled = true) { arrivalModifier ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp).then(arrivalModifier),
            )
        }
    }
    // stringResource is empty until the resources load: announce the real text, once.
    LaunchedEffect(message) { if (message.isNotBlank()) announce(message) }
}

/** "Já tem uma conta? Entrar" and "Ainda não tem uma conta? Criar conta". */
@Composable
internal fun AccountSwitch(
    question: String,
    action: String,
    onClick: () -> Unit,
) {
    Column {
        Text(question, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onClick, modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline()) {
            Text(action)
        }
    }
}

private val MaxFormWidth = 480.dp
private val BrandMarkSize = 40.dp
