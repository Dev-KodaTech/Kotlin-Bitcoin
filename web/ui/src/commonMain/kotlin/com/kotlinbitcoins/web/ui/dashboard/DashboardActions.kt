package com.kotlinbitcoins.web.ui.dashboard

import com.kotlinbitcoins.web.core.navigation.AppRoute

/** What the dashboard can ask: retry a block, pause or resume the auto-refresh and open an operation. */
class DashboardActions(
    val onRetryBalance: () -> Unit,
    val onRetryQuote: () -> Unit,
    val onRetryHistory: () -> Unit,
    val onRetryRecent: () -> Unit,
    val onToggleAutoRefresh: () -> Unit,
    val onNavigate: (AppRoute) -> Unit,
)
