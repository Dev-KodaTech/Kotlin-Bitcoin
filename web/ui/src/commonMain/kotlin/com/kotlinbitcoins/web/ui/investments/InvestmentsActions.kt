package com.kotlinbitcoins.web.ui.investments

import com.kotlinbitcoins.web.core.navigation.AppRoute

/** What "Meus investimentos" can ask: read the position again, pause or resume the auto-refresh and go to a page. */
class InvestmentsActions(
    val onRetry: () -> Unit,
    val onToggleAutoRefresh: () -> Unit,
    val onNavigate: (AppRoute) -> Unit,
)
