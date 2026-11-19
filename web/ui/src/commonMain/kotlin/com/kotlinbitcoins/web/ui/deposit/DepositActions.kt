package com.kotlinbitcoins.web.ui.deposit

import com.kotlinbitcoins.web.core.navigation.AppRoute

/** What the deposit page can ask: edit and confirm the amount, retry and start a new deposit. */
class DepositActions(
    val onAmountChange: (String) -> Unit,
    val onConfirm: () -> Unit,
    val onRetry: () -> Unit,
    val onEdit: () -> Unit,
    val onNewOperation: () -> Unit,
    val page: DepositPageActions,
)

/** What the page around the operation can ask: read the balance or the newest deposits again, and leave. */
class DepositPageActions(
    val onRetryBalance: () -> Unit,
    val onRetryRecentDeposits: () -> Unit,
    val onNavigate: (AppRoute) -> Unit,
    /** Writes to the assertive live region (alert region of the uncertain result). */
    val announce: (String) -> Unit,
)
