package com.kotlinbitcoins.web.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.model.EntryType
import com.kotlinbitcoins.web.core.model.InvestmentPosition
import com.kotlinbitcoins.web.core.model.StatementEntry
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.dashboard.DashboardUiState
import com.kotlinbitcoins.web.state.dashboard.Greeting
import com.kotlinbitcoins.web.state.dashboard.PositionTotal
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.ui.chart.PriceHistoryChart
import com.kotlinbitcoins.web.ui.chart.chartSummary
import com.kotlinbitcoins.web.ui.components.CardPadding
import com.kotlinbitcoins.web.ui.components.ErrorBlock
import com.kotlinbitcoins.web.ui.components.LoadableBlock
import com.kotlinbitcoins.web.ui.components.LoadingIndicator
import com.kotlinbitcoins.web.ui.components.MinTouchTarget
import com.kotlinbitcoins.web.ui.components.PageBottomPadding
import com.kotlinbitcoins.web.ui.components.PageHeading
import com.kotlinbitcoins.web.ui.components.PauseToggle
import com.kotlinbitcoins.web.ui.components.QuoteBlock
import com.kotlinbitcoins.web.ui.components.SectionCard
import com.kotlinbitcoins.web.ui.components.SectionTitle
import com.kotlinbitcoins.web.ui.components.VariationBadge
import com.kotlinbitcoins.web.ui.components.focusOutline
import com.kotlinbitcoins.web.ui.components.spokenText
import com.kotlinbitcoins.web.ui.icons.AppIcons
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.brasilia_time
import com.kotlinbitcoins.web.ui.resources.dashboard_balance
import com.kotlinbitcoins.web.ui.resources.dashboard_depends_on_quote
import com.kotlinbitcoins.web.ui.resources.dashboard_greeting_anonymous
import com.kotlinbitcoins.web.ui.resources.dashboard_greeting_named
import com.kotlinbitcoins.web.ui.resources.dashboard_investment_description
import com.kotlinbitcoins.web.ui.resources.dashboard_investment_detail
import com.kotlinbitcoins.web.ui.resources.dashboard_no_investments
import com.kotlinbitcoins.web.ui.resources.dashboard_position_total
import com.kotlinbitcoins.web.ui.resources.dashboard_recent
import com.kotlinbitcoins.web.ui.resources.dashboard_recent_description
import com.kotlinbitcoins.web.ui.resources.dashboard_recent_empty
import com.kotlinbitcoins.web.ui.resources.dashboard_shortcut_deposit
import com.kotlinbitcoins.web.ui.resources.dashboard_shortcut_purchase
import com.kotlinbitcoins.web.ui.resources.dashboard_shortcut_redemption
import com.kotlinbitcoins.web.ui.resources.dashboard_view_investments
import com.kotlinbitcoins.web.ui.resources.dashboard_view_market
import com.kotlinbitcoins.web.ui.resources.dashboard_view_statement
import com.kotlinbitcoins.web.ui.resources.investments_empty
import com.kotlinbitcoins.web.ui.resources.investments_list
import com.kotlinbitcoins.web.ui.resources.market_chart_title
import com.kotlinbitcoins.web.ui.resources.market_empty
import com.kotlinbitcoins.web.ui.resources.page_dashboard
import com.kotlinbitcoins.web.ui.resources.quote_unavailable
import com.kotlinbitcoins.web.ui.resources.retry
import com.kotlinbitcoins.web.ui.resources.statement_type_deposit
import com.kotlinbitcoins.web.ui.resources.statement_type_purchase
import com.kotlinbitcoins.web.ui.resources.statement_type_redemption
import com.kotlinbitcoins.web.ui.resources.statement_type_reinvestment
import com.kotlinbitcoins.web.ui.shared.message
import com.kotlinbitcoins.web.ui.theme.LocalExtendedColors
import com.kotlinbitcoins.web.ui.theme.numeric
import org.jetbrains.compose.resources.stringResource

/** From this width on, the summary blocks go to a column on the right of the main ones. */
private val TwoColumnsMinWidth = 880.dp
private val SectionGap = 24.dp
private val HeroPadding = 28.dp
private val IconWellSize = 40.dp
private const val MAIN_COLUMN_WEIGHT = 1.7f
private const val MAX_INVESTMENTS = 3

/**
 * Dashboard (US2) in the "Azul suave" look: the greeting (h1) with the pause toggle; the balance as the hero block
 * with the shortcuts "Depositar", "Comprar" and "Resgatar"; the quote; the 24 h price chart; and, in a column on the
 * right on wide screens, the current total, the open investments and the newest statement entries. Each block has
 * its own state (FR-013). The structure never changes between refreshes: only the values do, so the keyboard focus
 * stays where it was (US2-2). Stateless.
 */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    actions: DashboardActions,
) {
    BoxWithConstraints {
        val twoColumns = maxWidth >= TwoColumnsMinWidth
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = PageBottomPadding),
            verticalArrangement = Arrangement.spacedBy(SectionGap),
        ) {
            Header(state, actions)
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(SectionGap)) {
                    Column(
                        modifier = Modifier.weight(MAIN_COLUMN_WEIGHT),
                        verticalArrangement = Arrangement.spacedBy(SectionGap),
                    ) { MainBlocks(state, actions) }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SectionGap),
                    ) { SummaryBlocks(state, actions) }
                }
            } else {
                MainBlocks(state, actions)
                SummaryBlocks(state, actions)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(
    state: DashboardUiState,
    actions: DashboardActions,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(Res.string.page_dashboard),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PageHeading(state.greeting.text())
        }
        PauseToggle(isPaused = state.isAutoRefreshPaused, onToggle = actions.onToggleAutoRefresh)
    }
}

@Composable
private fun MainBlocks(
    state: DashboardUiState,
    actions: DashboardActions,
) {
    BalanceHero(state, actions)
    QuoteBlock(state.quote, onRetry = actions.onRetryQuote)
    SectionCard {
        CardHeader(
            title = stringResource(Res.string.market_chart_title),
            link = stringResource(Res.string.dashboard_view_market),
            onLink = { actions.onNavigate(AppRoute.Market) },
        )
        LoadableBlock(
            state = state.history,
            onRetry = actions.onRetryHistory,
            empty = { Text(stringResource(Res.string.market_empty), style = MaterialTheme.typography.bodyLarge) },
        ) { chart ->
            PriceHistoryChart(chart, chartSummary(chart), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SummaryBlocks(
    state: DashboardUiState,
    actions: DashboardActions,
) {
    SectionCard(
        title = stringResource(Res.string.dashboard_position_total),
        color = LocalExtendedColors.current.tint,
    ) {
        PositionTotalContent(state.position, actions)
    }
    SectionCard {
        CardHeader(
            title = stringResource(Res.string.investments_list),
            link = stringResource(Res.string.dashboard_view_investments),
            onLink = { actions.onNavigate(AppRoute.Investments) },
        )
        InvestmentsContent(state)
    }
    SectionCard {
        CardHeader(
            title = stringResource(Res.string.dashboard_recent),
            link = stringResource(Res.string.dashboard_view_statement),
            onLink = { actions.onNavigate(AppRoute.Statement()) },
        )
        LoadableBlock(
            state = state.recent,
            onRetry = actions.onRetryRecent,
            empty = { MutedText(stringResource(Res.string.dashboard_recent_empty)) },
        ) { entries ->
            Rows(entries) { RecentEntryRow(it) }
        }
    }
}

@Composable
private fun Greeting.text(): String =
    when (this) {
        is Greeting.Named -> stringResource(Res.string.dashboard_greeting_named, firstName)
        Greeting.Anonymous -> stringResource(Res.string.dashboard_greeting_anonymous)
    }

/** The balance on the filled block of the page, with the three shortcuts in white. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BalanceHero(
    state: DashboardUiState,
    actions: DashboardActions,
) {
    val extended = LocalExtendedColors.current
    Surface(
        color = extended.hero,
        contentColor = extended.onHero,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        FlowRow(
            modifier = Modifier.padding(HeroPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(Res.string.dashboard_balance),
                    style = MaterialTheme.typography.titleSmall,
                    color = extended.onHeroMuted,
                    modifier = Modifier.semantics { heading() },
                )
                when (val balance = state.balance) {
                    Loadable.Loading -> {
                        LoadingIndicator()
                    }

                    Loadable.Empty -> {}

                    is Loadable.Loaded -> {
                        Text(balance.value.format(), style = MaterialTheme.typography.displaySmall)
                    }

                    is Loadable.Failed -> {
                        Text(balance.error.message(), style = MaterialTheme.typography.bodyLarge)
                        HeroOutlinedButton(stringResource(Res.string.retry), actions.onRetryBalance)
                    }
                }
            }
            Shortcuts(actions.onNavigate)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Shortcuts(onNavigate: (AppRoute) -> Unit) {
    val extended = LocalExtendedColors.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val colors =
            ButtonDefaults.buttonColors(containerColor = extended.heroAction, contentColor = extended.onHeroAction)
        // The ring is drawn inside the white button, so it takes the color of its text.
        val modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline(extended.onHeroAction)
        Button(
            onClick = { onNavigate(AppRoute.Deposit) },
            colors = colors,
            shape = MaterialTheme.shapes.medium,
            modifier = modifier,
        ) { Text(stringResource(Res.string.dashboard_shortcut_deposit)) }
        Button(
            onClick = { onNavigate(AppRoute.Purchase) },
            colors = colors,
            shape = MaterialTheme.shapes.medium,
            modifier = modifier,
        ) { Text(stringResource(Res.string.dashboard_shortcut_purchase)) }
        HeroOutlinedButton(stringResource(Res.string.dashboard_shortcut_redemption)) {
            onNavigate(AppRoute.Redemption)
        }
    }
}

@Composable
private fun HeroOutlinedButton(
    text: String,
    onClick: () -> Unit,
) {
    val onHero = LocalExtendedColors.current.onHero
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = onHero),
        border = BorderStroke(1.5.dp, onHero),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline(onHero),
    ) { Text(text) }
}

@Composable
private fun PositionTotalContent(
    position: PositionTotal,
    actions: DashboardActions,
) {
    val valueStyle = MaterialTheme.typography.headlineMedium
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

/** The first open investments, or one line saying why there is none to show; the total block has the retry. */
@Composable
private fun InvestmentsContent(state: DashboardUiState) {
    when (val position = state.position) {
        PositionTotal.Loading -> LoadingIndicator()
        is PositionTotal.Value -> Rows(state.investments.take(MAX_INVESTMENTS)) { InvestmentRow(it) }
        PositionTotal.DependsOnQuote -> MutedText(stringResource(Res.string.quote_unavailable))
        is PositionTotal.NoInvestments -> MutedText(stringResource(Res.string.investments_empty))
        is PositionTotal.Failed -> MutedText(position.error.message())
    }
}

/** Title of a card with a link to the page that has everything about it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardHeader(
    title: String,
    link: String,
    onLink: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle(title)
        TextButton(onClick = onLink, modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline()) {
            Text(link)
        }
    }
}

@Composable
private fun <T> Rows(
    items: List<T>,
    row: @Composable (T) -> Unit,
) {
    Column {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            row(item)
        }
    }
}

/** One open investment, read at once: current value, invested amount, purchase date and variation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InvestmentRow(investment: InvestmentPosition) {
    val description =
        stringResource(
            Res.string.dashboard_investment_description,
            investment.currentValue.format(),
            investment.investedAmount.format(),
            investment.purchasedAt.format(),
            investment.variationPercent.spokenText(),
        )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clearAndSetSemantics { contentDescription = description },
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(investment.currentValue.format(), style = MaterialTheme.typography.titleMedium.numeric())
            VariationBadge(investment.variationPercent, style = MaterialTheme.typography.labelLarge)
        }
        Text(
            text =
                stringResource(
                    Res.string.dashboard_investment_detail,
                    investment.purchasedAt.format(),
                    investment.investedAmount.format(),
                ),
            style = MaterialTheme.typography.bodySmall.numeric(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One statement entry, read at once: type, signed amount and when. */
@Composable
private fun RecentEntryRow(entry: StatementEntry) {
    val type = stringResource(entry.type.label())
    val amount = entry.type.sign() + entry.amount.format()
    val description =
        stringResource(
            Res.string.dashboard_recent_description,
            type,
            amount,
            entry.occurredAt.format(),
            stringResource(Res.string.brasilia_time),
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(IconWellSize)
                    .background(LocalExtendedColors.current.tint, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(entry.type.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(type, style = MaterialTheme.typography.labelLarge)
            Text(
                text = entry.occurredAt.format(),
                style = MaterialTheme.typography.bodySmall.numeric(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(amount, style = MaterialTheme.typography.titleSmall.numeric())
    }
}

@Composable
private fun MutedText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = CardPadding / 2),
    )
}

private fun EntryType.label() =
    when (this) {
        EntryType.Deposit -> Res.string.statement_type_deposit
        EntryType.Purchase -> Res.string.statement_type_purchase
        EntryType.Redemption -> Res.string.statement_type_redemption
        EntryType.Reinvestment -> Res.string.statement_type_reinvestment
    }

private fun EntryType.icon(): ImageVector =
    when (this) {
        EntryType.Deposit -> AppIcons.Deposit
        EntryType.Purchase -> AppIcons.Purchase
        EntryType.Redemption -> AppIcons.Redemption
        EntryType.Reinvestment -> AppIcons.Reinvestment
    }

/** What the entry does to the balance: it comes in, it goes out, or (a reinvestment, FR-029) it does not change. */
private fun EntryType.sign(): String =
    when (this) {
        EntryType.Deposit, EntryType.Redemption -> "+ "
        EntryType.Purchase -> "− "
        EntryType.Reinvestment -> ""
    }
