package com.kotlinbitcoins.web.state.port

import kotlinx.coroutines.flow.StateFlow

/** Whether the page is visible (`document.visibilityState`); hidden pages do not auto-refresh (W-10). */
interface PageVisibility {
    val isVisible: StateFlow<Boolean>
}
