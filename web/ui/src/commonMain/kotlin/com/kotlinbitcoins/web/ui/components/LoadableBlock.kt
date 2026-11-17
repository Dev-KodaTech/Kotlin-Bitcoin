package com.kotlinbitcoins.web.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.ui.shared.ErrorContext
import com.kotlinbitcoins.web.ui.shared.message

/** The four distinct states of a block that loads data (FR-042). Each block fails alone (FR-013). */
@Composable
fun <T> LoadableBlock(
    state: Loadable<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    errorContext: ErrorContext = ErrorContext.General,
    empty: @Composable () -> Unit = {},
    content: @Composable (T) -> Unit,
) {
    when (state) {
        Loadable.Loading -> LoadingIndicator(modifier)
        Loadable.Empty -> empty()
        is Loadable.Failed -> ErrorBlock(state.error.message(errorContext), modifier, onRetry)
        is Loadable.Loaded -> content(state.value)
    }
}
