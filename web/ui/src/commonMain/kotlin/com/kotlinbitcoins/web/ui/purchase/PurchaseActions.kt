package com.kotlinbitcoins.web.ui.purchase

import com.kotlinbitcoins.web.core.navigation.AppRoute

/** What the purchase page can ask: edit, review and confirm the amount, retry and start a new purchase. */
class PurchaseActions(
    val onAmountChange: (String) -> Unit,
    val onReview: () -> Unit,
    val onConfirm: () -> Unit,
    val onEdit: () -> Unit,
    val onRetry: () -> Unit,
    val onNewOperation: () -> Unit,
    val page: PurchasePageActions,
)

/** What the page around the operation can ask: reload the balance, pause the quote and leave. */
class PurchasePageActions(
    val onRetryBalance: () -> Unit,
    val onToggleAutoRefresh: () -> Unit,
    val onNavigate: (AppRoute) -> Unit,
    /** Writes to the assertive live region (alert region of the uncertain result). */
    val announce: (String) -> Unit,
)
