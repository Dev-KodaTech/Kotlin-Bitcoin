package com.kotlinbitcoins.web.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.ui.icons.AppIcons
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.nav_dashboard
import com.kotlinbitcoins.web.ui.resources.nav_deposit
import com.kotlinbitcoins.web.ui.resources.nav_investments
import com.kotlinbitcoins.web.ui.resources.nav_market
import com.kotlinbitcoins.web.ui.resources.nav_purchase
import com.kotlinbitcoins.web.ui.resources.nav_redemption
import com.kotlinbitcoins.web.ui.resources.nav_statement
import com.kotlinbitcoins.web.ui.resources.page_dashboard
import com.kotlinbitcoins.web.ui.resources.page_deposit
import com.kotlinbitcoins.web.ui.resources.page_investments
import com.kotlinbitcoins.web.ui.resources.page_login
import com.kotlinbitcoins.web.ui.resources.page_market
import com.kotlinbitcoins.web.ui.resources.page_purchase
import com.kotlinbitcoins.web.ui.resources.page_redemption
import com.kotlinbitcoins.web.ui.resources.page_register
import com.kotlinbitcoins.web.ui.resources.page_statement
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A destination of the navigation menu (FR-009), in the order of contracts/ui-routes.md. */
internal data class NavDestinationItem(
    val route: AppRoute,
    val label: StringResource,
    val icon: ImageVector,
)

internal val navigationItems =
    listOf(
        NavDestinationItem(AppRoute.Dashboard, Res.string.nav_dashboard, AppIcons.Home),
        NavDestinationItem(AppRoute.Deposit, Res.string.nav_deposit, AppIcons.Deposit),
        NavDestinationItem(AppRoute.Purchase, Res.string.nav_purchase, AppIcons.Purchase),
        NavDestinationItem(AppRoute.Investments, Res.string.nav_investments, AppIcons.Investments),
        NavDestinationItem(AppRoute.Redemption, Res.string.nav_redemption, AppIcons.Redemption),
        NavDestinationItem(AppRoute.Statement(), Res.string.nav_statement, AppIcons.Statement),
        NavDestinationItem(AppRoute.Market, Res.string.nav_market, AppIcons.Market),
    )

/** Same menu item for every statement filter. */
internal fun AppRoute.isSameDestination(other: AppRoute): Boolean = this::class == other::class

/** Name of the page: h1 and `document.title` (contracts/ui-routes.md). */
@Composable
fun AppRoute.pageName(): String =
    stringResource(
        when (this) {
            is AppRoute.Login -> Res.string.page_login
            AppRoute.Register -> Res.string.page_register
            AppRoute.Dashboard -> Res.string.page_dashboard
            AppRoute.Deposit -> Res.string.page_deposit
            AppRoute.Purchase -> Res.string.page_purchase
            AppRoute.Investments -> Res.string.page_investments
            AppRoute.Redemption -> Res.string.page_redemption
            is AppRoute.Statement -> Res.string.page_statement
            AppRoute.Market -> Res.string.page_market
        },
    )

/** The typed route of a back stack entry (navigation-compose has no polymorphic `toRoute` for a sealed type). */
fun NavBackStackEntry.toAppRoute(): AppRoute? {
    val destination = destination
    return when {
        destination.hasRoute<AppRoute.Login>() -> toRoute<AppRoute.Login>()
        destination.hasRoute<AppRoute.Register>() -> AppRoute.Register
        destination.hasRoute<AppRoute.Dashboard>() -> AppRoute.Dashboard
        destination.hasRoute<AppRoute.Deposit>() -> AppRoute.Deposit
        destination.hasRoute<AppRoute.Purchase>() -> AppRoute.Purchase
        destination.hasRoute<AppRoute.Investments>() -> AppRoute.Investments
        destination.hasRoute<AppRoute.Redemption>() -> AppRoute.Redemption
        destination.hasRoute<AppRoute.Statement>() -> toRoute<AppRoute.Statement>()
        destination.hasRoute<AppRoute.Market>() -> AppRoute.Market
        else -> null
    }
}
