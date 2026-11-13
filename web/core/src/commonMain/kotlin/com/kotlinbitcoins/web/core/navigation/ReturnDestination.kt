package com.kotlinbitcoins.web.core.navigation

/**
 * Where to go after login (FR-007, W-04). Only a protected route of the app is accepted; anything else (absolute
 * URL, `//host`, `javascript:`, public or unknown route, double encoded value) goes to the dashboard. Since the
 * result is a typed [AppRoute], an external destination is impossible by construction.
 */
object ReturnDestination {
    fun resolve(raw: String?): AppRoute =
        raw
            ?.takeIf { it.startsWith('/') }
            ?.let(AppRoute::parseOrNull)
            ?.takeIf { it.isProtected }
            ?: AppRoute.Dashboard
}
