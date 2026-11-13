package com.kotlinbitcoins.web.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.loading
import com.kotlinbitcoins.web.ui.theme.LocalReducedMotion
import org.jetbrains.compose.resources.stringResource

private val SpinnerSize = 20.dp

/** "Carregando…"; the spinner is decorative and disappears with reduced motion (FR-045). */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!LocalReducedMotion.current) {
            CircularProgressIndicator(modifier = Modifier.size(SpinnerSize).clearAndSetSemantics { })
        }
        Text(stringResource(Res.string.loading))
    }
}
