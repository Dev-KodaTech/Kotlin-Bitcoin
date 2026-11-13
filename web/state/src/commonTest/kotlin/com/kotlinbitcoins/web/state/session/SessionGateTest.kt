package com.kotlinbitcoins.web.state.session

import com.kotlinbitcoins.web.core.navigation.AppRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class SessionGateTest {
    private val active = SessionState.Active(Session("token", Instant.fromEpochMilliseconds(1)))
    private val anonymous = SessionState.Anonymous(AnonymousReason.None)
    private val protectedRoutes =
        listOf(
            AppRoute.Dashboard,
            AppRoute.Deposit,
            AppRoute.Purchase,
            AppRoute.Investments,
            AppRoute.Redemption,
            AppRoute.Statement(),
            AppRoute.Statement("2026-10-01", "2026-11-09"),
            AppRoute.Market,
        )

    @Test
    fun protectedRouteWithoutSessionGoesToLoginWithTheReturnFragment() {
        assertEquals(
            AppRoute.Login(returnTo = "/extrato?de=2026-10-01&ate=2026-11-09"),
            SessionGate.resolve(AppRoute.Statement("2026-10-01", "2026-11-09"), anonymous),
        )
        assertEquals(AppRoute.Login(returnTo = "/painel"), SessionGate.resolve(AppRoute.Dashboard, anonymous))
        protectedRoutes.forEach { route ->
            listOf(AnonymousReason.None, AnonymousReason.Expired, AnonymousReason.LoggedOut).forEach { reason ->
                val resolved = SessionGate.resolve(route, SessionState.Anonymous(reason))
                assertEquals(AppRoute.Login(returnTo = route.toFragment().removePrefix("#")), resolved)
            }
        }
    }

    @Test
    fun publicRoutesWithoutSessionAreShown() {
        assertEquals(AppRoute.Login(), SessionGate.resolve(AppRoute.Login(), anonymous))
        assertEquals(AppRoute.Register, SessionGate.resolve(AppRoute.Register, anonymous))
    }

    @Test
    fun protectedRoutesWithSessionAreShown() {
        protectedRoutes.forEach { route -> assertEquals(route, SessionGate.resolve(route, active)) }
    }

    @Test
    fun publicRoutesWithSessionGoToTheDashboardOrTheReturnDestination() {
        assertEquals(AppRoute.Dashboard, SessionGate.resolve(AppRoute.Login(), active))
        assertEquals(AppRoute.Dashboard, SessionGate.resolve(AppRoute.Register, active))
        assertEquals(AppRoute.Market, SessionGate.resolve(AppRoute.Login(returnTo = "/mercado"), active))
        assertEquals(
            AppRoute.Dashboard,
            SessionGate.resolve(AppRoute.Login(returnTo = "https://evil.example"), active),
        )
    }

    @Test
    fun nothingIsShownWhileResolving() {
        (protectedRoutes + AppRoute.Login() + AppRoute.Register).forEach { route ->
            assertNull(SessionGate.resolve(route, SessionState.Resolving), "$route")
        }
    }

    @Test
    fun expiredOnAFilteredStatementGoesToLoginWithReturnAndBanner() {
        assertEquals(
            SessionGate.Redirect(
                AppRoute.Login(returnTo = "/extrato?de=2026-10-01&ate=2026-11-09"),
                LoginBanner.SessionExpired,
            ),
            SessionGate.onEvent(SessionEvent.Expired, AppRoute.Statement("2026-10-01", "2026-11-09")),
        )
    }

    @Test
    fun expiredOnTheLoginPageKeepsItsReturn() {
        assertEquals(
            SessionGate.Redirect(AppRoute.Login(returnTo = "/mercado"), LoginBanner.SessionExpired),
            SessionGate.onEvent(SessionEvent.Expired, AppRoute.Login(returnTo = "/mercado")),
        )
    }

    @Test
    fun loggedOutGoesToLoginWithoutReturnNorBanner() {
        assertEquals(
            SessionGate.Redirect(AppRoute.Login(), banner = null),
            SessionGate.onEvent(SessionEvent.LoggedOut, AppRoute.Statement("2026-10-01", "2026-11-09")),
        )
    }

    @Test
    fun loggedInFromAnotherTabLeavesThePublicPages() {
        assertEquals(
            SessionGate.Redirect(AppRoute.Investments, banner = null),
            SessionGate.onEvent(SessionEvent.LoggedIn, AppRoute.Login(returnTo = "/investimentos")),
        )
        assertEquals(
            SessionGate.Redirect(AppRoute.Dashboard, banner = null),
            SessionGate.onEvent(SessionEvent.LoggedIn, AppRoute.Register),
        )
        assertNull(SessionGate.onEvent(SessionEvent.LoggedIn, AppRoute.Market))
    }
}
