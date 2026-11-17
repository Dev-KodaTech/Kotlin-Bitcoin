package com.kotlinbitcoins.web.ui.market

/** What the Mercado can ask: refresh both blocks, retry one block and show or hide the data table. */
class MarketActions(
    val onRefresh: () -> Unit,
    val onRetryVolume: () -> Unit,
    val onRetryHistory: () -> Unit,
    val onToggleTable: () -> Unit,
)
