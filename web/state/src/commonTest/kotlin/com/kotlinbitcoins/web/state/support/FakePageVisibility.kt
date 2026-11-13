package com.kotlinbitcoins.web.state.support

import com.kotlinbitcoins.web.state.port.PageVisibility
import kotlinx.coroutines.flow.MutableStateFlow

class FakePageVisibility(
    visible: Boolean = true,
) : PageVisibility {
    override val isVisible = MutableStateFlow(visible)
}
