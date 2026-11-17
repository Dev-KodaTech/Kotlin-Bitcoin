package com.kotlinbitcoins.web.ui.statement

/** What the statement can ask: edit the dates, apply or clear the filter and read the period again. */
class StatementActions(
    val onFromChange: (String) -> Unit,
    val onToChange: (String) -> Unit,
    val onApplyFilter: () -> Unit,
    val onClearFilter: () -> Unit,
    val onRetry: () -> Unit,
)
