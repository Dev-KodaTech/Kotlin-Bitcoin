package com.kotlinbitcoins.web.e2e.auth

import com.kotlinbitcoins.web.e2e.support.E2eBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** FR-007: only internal destinations are accepted as the return after login (T111, contracts/ui-routes.md rule 6). */
class ReturnDestinationE2ETest : E2eBase() {
    @ParameterizedTest
    @MethodSource("browsers")
    fun `external or script destinations go to the dashboard and never leave the origin`(browserName: String) {
        val email = api.uniqueEmail().also { api.register("Ana", it, PASSWORD) }
        listOf(
            "https%3A%2F%2Fevil.example",
            "%2F%2Fevil.example",
            "javascript%3Aalert(1)",
        ).forEach { returnTo ->
            val navigations = mutableListOf<String>()
            withPage(browserName) {
                page.onFrameNavigated { if (it == page.mainFrame()) navigations += it.url() }
                open("#/entrar?voltar=$returnTo")
                waitForLogin()

                login(email)

                waitForDashboard()
                assertThat(page.url()).describedAs(returnTo).startsWith(baseUrl)
                assertThat(navigations).describedAs(returnTo).allSatisfy { assertThat(it).startsWith(baseUrl) }
            }
        }
    }
}
