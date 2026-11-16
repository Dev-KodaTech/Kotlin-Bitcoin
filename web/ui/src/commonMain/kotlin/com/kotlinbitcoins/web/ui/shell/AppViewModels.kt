package com.kotlinbitcoins.web.ui.shell

import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.account.LoginViewModel
import com.kotlinbitcoins.web.state.account.RegisterViewModel
import com.kotlinbitcoins.web.state.dashboard.DashboardTexts
import com.kotlinbitcoins.web.state.dashboard.DashboardViewModel
import com.kotlinbitcoins.web.state.deposit.DepositTexts
import com.kotlinbitcoins.web.state.deposit.DepositViewModel
import com.kotlinbitcoins.web.state.investments.InvestmentsTexts
import com.kotlinbitcoins.web.state.investments.InvestmentsViewModel
import com.kotlinbitcoins.web.state.market.MarketViewModel
import com.kotlinbitcoins.web.state.purchase.PurchaseTexts
import com.kotlinbitcoins.web.state.purchase.PurchaseViewModel
import com.kotlinbitcoins.web.state.redemption.RedemptionTexts
import com.kotlinbitcoins.web.state.redemption.RedemptionViewModel
import com.kotlinbitcoins.web.state.session.LoginBanner
import com.kotlinbitcoins.web.state.statement.StatementViewModel

/**
 * Factories of the ViewModels of each destination, built by `AppContainer` in :app (the only place that knows the
 * gateways of :data). Each ViewModel lives as long as its back stack entry. Each user story adds its factory.
 */
@Suppress("LongParameterList") // One factory per destination by design (no DI library, Principle V).
class AppViewModels(
    val register: () -> RegisterViewModel,
    val login: (returnTo: String?, banner: LoginBanner?) -> LoginViewModel,
    /** The announced texts come from the string resources of :ui, read when announced. */
    val dashboard: (texts: () -> DashboardTexts) -> DashboardViewModel,
    val deposit: (texts: () -> DepositTexts) -> DepositViewModel,
    val purchase: (texts: () -> PurchaseTexts) -> PurchaseViewModel,
    val investments: (texts: () -> InvestmentsTexts) -> InvestmentsViewModel,
    val redemption: (texts: () -> RedemptionTexts) -> RedemptionViewModel,
    /** The filter comes from the route (`de`/`ate`), which is the only representation of it (FR-027). */
    val statement: (route: AppRoute.Statement) -> StatementViewModel,
    val market: () -> MarketViewModel,
)
