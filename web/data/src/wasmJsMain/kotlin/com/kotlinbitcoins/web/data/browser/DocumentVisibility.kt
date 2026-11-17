package com.kotlinbitcoins.web.data.browser

import com.kotlinbitcoins.web.state.port.PageVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** `document.visibilityState` as a flow: a hidden tab stops the auto-refresh (W-10). */
class DocumentVisibility : PageVisibility {
    private val visible = MutableStateFlow(isDocumentVisible())

    override val isVisible: StateFlow<Boolean> = visible.asStateFlow()

    init {
        onVisibilityChange { visible.value = isDocumentVisible() }
    }
}
