package com.kotlinbitcoins.web.state.session

import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.core.navigation.ReturnDestination

/**
 * Session guard of the navigation (W-04, contracts/ui-routes.md rules 4 and 5). Applied at start-up, on every
 * destination change and on every [SessionEvent].
 */
object SessionGate {
    data class Redirect(
        val route: AppRoute,
        val banner: LoginBanner?,
    )

    /**
     * The route to show for [requested]. Null while the session is resolving: only the loading state is shown,
     * so no protected content (nor a login page that would flash) appears.
     */
    fun resolve(
        requested: AppRoute,
        state: SessionState,
    ): AppRoute? =
        when (state) {
            SessionState.Resolving -> {
                null
            }

            is SessionState.Active -> {
                when (requested) {
                    is AppRoute.Login -> ReturnDestination.resolve(requested.returnTo)
                    AppRoute.Register -> AppRoute.Dashboard
                    else -> requested
                }
            }

            is SessionState.Anonymous -> {
                if (requested.isProtected) loginFor(requested) else requested
            }
        }

    /** Where to go when [event] happens while [current] is shown; null keeps the current route. */
    fun onEvent(
        event: SessionEvent,
        current: AppRoute,
    ): Redirect? =
        when (event) {
            SessionEvent.Expired -> {
                val returnTo = if (current is AppRoute.Login) current.returnTo else loginFor(current).returnTo
                Redirect(AppRoute.Login(returnTo = returnTo), LoginBanner.SessionExpired)
            }

            SessionEvent.LoggedOut -> {
                Redirect(AppRoute.Login(), banner = null)
            }

            SessionEvent.LoggedIn -> {
                when (current) {
                    is AppRoute.Login -> Redirect(ReturnDestination.resolve(current.returnTo), banner = null)
                    AppRoute.Register -> Redirect(AppRoute.Dashboard, banner = null)
                    else -> null
                }
            }
        }

    private fun loginFor(route: AppRoute): AppRoute.Login =
        AppRoute.Login(returnTo = route.takeIf { it.isProtected }?.toFragment()?.removePrefix("#"))
}
