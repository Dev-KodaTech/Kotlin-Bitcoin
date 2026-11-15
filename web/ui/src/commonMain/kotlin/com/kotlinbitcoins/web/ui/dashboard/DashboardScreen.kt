package com.kotlinbitcoins.web.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.dashboard.DashboardUiState
import com.kotlinbitcoins.web.state.dashboard.Greeting
import com.kotlinbitcoins.web.state.dashboard.PositionTotal
import com.kotlinbitcoins.web.ui.components.ErrorBlock
import com.kotlinbitcoins.web.ui.components.LoadableBlock
import com.kotlinbitcoins.web.ui.components.LoadingIndicator
import com.kotlinbitcoins.web.ui.components.MinTouchTarget
import com.kotlinbitcoins.web.ui.components.PageHeading
import com.kotlinbitcoins.web.ui.components.PauseToggle
import com.kotlinbitcoins.web.ui.components.QuoteBlock
import com.kotlinbitcoins.web.ui.components.focusOutline
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.dashboard_balance
import com.kotlinbitcoins.web.ui.resources.dashboard_depends_on_quote
import com.kotlinbitcoins.web.ui.resources.dashboard_greeting_anonymous
import com.kotlinbitcoins.web.ui.resources.dashboard_greeting_named
import com.kotlinbitcoins.web.ui.resources.dashboard_no_investments
import com.kotlinbitcoins.web.ui.resources.dashboard_position_total
import com.kotlinbitcoins.web.ui.resources.dashboard_shortcut_deposit
import com.kotlinbitcoins.web.ui.resources.dashboard_shortcut_purchase
import com.kotlinbitcoins.web.ui.resources.dashboard_shortcut_redemption
import com.kotlinbitcoins.web.ui.resources.dashboard_shortcuts
import com.kotlinbitcoins.web.ui.shared.message
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.stringResource

/**
 * Dashboard (US2): h1 with the greeting, the pause toggle, balance, quote and current total, each block with its
 * own state (FR-013), and the shortcuts "Depositar", "Comprar" and "Resgatar". The structure never changes between
 * refreshes: only the values do, so the keyboard focus stays where it was (US2-2). Stateless.
 */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    actions: DashboardActions,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeading(state.greeting.text())
        PauseToggle(isPaused = state.isAutoRefreshPaused, onToggle = actions.onToggleAutoRefresh)

        BlockCard(stringResource(Res.string.dashboard_balance)) {
            LoadableBlock(state.balance, onRetry = actions.onRetryBalance) { balance ->
                Text(balance.format(), style = MaterialTheme.typography.headlineSmall.numeric())
            }
        }

        QuoteBlock(state.quote, onRetry = actions.onRetryQuote)

        BlockCard(stringResource(Res.string.dashboard_position_total)) {
            PositionTotalContent(state.position, actions)
        }

        Shortcuts(actions.onNavigate)
    }
}

@Composable
private fun Greeting.text(): String =
    when (this) {
        is Greeting.Named -> stringResource(Res.string.dashboard_greeting_named, firstName)
        Greeting.Anonymous -> stringResource(Res.string.dashboard_greeting_anonymous)
    }

@Composable
private fun PositionTotalContent(
    position: PositionTotal,
    actions: DashboardActions,
) {
    val valueStyle = MaterialTheme.typography.headlineSmall.numeric()
    when (position) {
        PositionTotal.Loading -> {
            LoadingIndicator()
        }

        is PositionTotal.Value -> {
            Text(position.total.format(), style = valueStyle)
        }

        PositionTotal.DependsOnQuote -> {
            Text(stringResource(Res.string.dashboard_depends_on_quote))
        }

        is PositionTotal.NoInvestments -> {
            Text(position.total.format(), style = valueStyle)
            Text(stringResource(Res.string.dashboard_no_investments))
        }

        is PositionTotal.Failed -> {
            ErrorBlock(position.error.message(), onRetry = actions.onRetryQuote)
        }
    }
}

@Composable
private fun BlockCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Shortcuts(onNavigate: (AppRoute) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.dashboard_shortcuts),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val buttonModifier = Modifier.heightIn(min = MinTouchTarget).focusOutline()
            Button(onClick = { onNavigate(AppRoute.Deposit) }, modifier = buttonModifier) {
                Text(stringResource(Res.string.dashboard_shortcut_deposit))
            }
            Button(onClick = { onNavigate(AppRoute.Purchase) }, modifier = buttonModifier) {
                Text(stringResource(Res.string.dashboard_shortcut_purchase))
            }
            OutlinedButton(onClick = { onNavigate(AppRoute.Redemption) }, modifier = buttonModifier) {
                Text(stringResource(Res.string.dashboard_shortcut_redemption))
            }
        }
    }
}
