package com.kotlinbitcoins.web.ui.deposit

import com.kotlinbitcoins.web.core.navigation.AppRoute

/** What the deposit page can ask: edit and confirm the amount, retry, start a new deposit and leave. */
class DepositActions(
    val onAmountChange: (String) -> Unit,
    val onConfirm: () -> Unit,
    val onRetry: () -> Unit,
    val onEdit: () -> Unit,
    val onNewOperation: () -> Unit,
    val onNavigate: (AppRoute) -> Unit,
    /** Writes to the assertive live region (alert region of the uncertain result). */
    val announce: (String) -> Unit,
)
