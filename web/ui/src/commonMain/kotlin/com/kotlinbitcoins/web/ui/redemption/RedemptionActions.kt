package com.kotlinbitcoins.web.ui.redemption

import com.kotlinbitcoins.web.core.navigation.AppRoute

/** What the redemption page can ask: edit, review and confirm the amount, retry and start a new redemption. */
class RedemptionActions(
    val onAmountChange: (String) -> Unit,
    val onReview: () -> Unit,
    val onConfirm: () -> Unit,
    val onEdit: () -> Unit,
    val onRetry: () -> Unit,
    val onNewOperation: () -> Unit,
    val page: RedemptionPageActions,
)

/**
 * What the page around the operation can ask: fill the field with the available ("Resgatar tudo"), reload it, pause
 * its refresh and leave.
 */
class RedemptionPageActions(
    val onRedeemAll: () -> Unit,
    val onRetryAvailable: () -> Unit,
    val onToggleAutoRefresh: () -> Unit,
    val onNavigate: (AppRoute) -> Unit,
    /** Writes to the assertive live region (alert region of the uncertain result). */
    val announce: (String) -> Unit,
)
