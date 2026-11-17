package com.kotlinbitcoins.web.state.port

/** What :data needs from the session, without depending on the concrete SessionManager (W-05). */
interface SessionTokenProvider {
    /** The token for an authenticated request, or null when there is no valid session (the request is not sent). */
    fun tokenForRequest(): String?

    /** An authenticated request got 401 UNAUTHORIZED: the session is over (FR-004). */
    fun onUnauthorized()
}
