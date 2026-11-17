package com.kotlinbitcoins.web.state.session

/** Session lifecycle (data-model §4.1). */
sealed interface SessionState {
    /** Looking for a session (storage or another tab, up to 300 ms): no protected route is shown. */
    data object Resolving : SessionState

    data class Active(
        val session: Session,
    ) : SessionState

    data class Anonymous(
        val reason: AnonymousReason,
    ) : SessionState
}

enum class AnonymousReason { None, Expired, LoggedOut }

/** Changes that the navigation reacts to (W-04). */
sealed interface SessionEvent {
    /** Logged in here or in another tab. */
    data object LoggedIn : SessionEvent

    /** "Sair" here or in another tab. */
    data object LoggedOut : SessionEvent

    /** Expiry instant reached, 401 UNAUTHORIZED, or expired in another tab. */
    data object Expired : SessionEvent
}
