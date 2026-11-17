package com.kotlinbitcoins.web.data.support

import com.kotlinbitcoins.web.state.port.SessionTokenProvider

class FakeSessionTokenProvider(
    var token: String? = "test-token",
) : SessionTokenProvider {
    var unauthorizedCalls = 0
        private set

    override fun tokenForRequest(): String? = token

    override fun onUnauthorized() {
        unauthorizedCalls++
    }
}
