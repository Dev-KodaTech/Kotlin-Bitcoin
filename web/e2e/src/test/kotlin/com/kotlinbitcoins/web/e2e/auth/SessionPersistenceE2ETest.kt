package com.kotlinbitcoins.web.e2e.auth

import com.kotlinbitcoins.web.e2e.support.AppPage
import com.kotlinbitcoins.web.e2e.support.E2eBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** US1: the session survives reloads and new tabs of the same browser, ends with it and never leaks (T109). */
class SessionPersistenceE2ETest : E2eBase() {
    private fun registered(): String = api.uniqueEmail().also { api.register("Ana", it, PASSWORD) }

    // US1-5
    @ParameterizedTest
    @MethodSource("browsers")
    fun `reload and a new tab of the same browser stay logged in`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            open("#/entrar")
            waitForLogin()
            login(email)
            waitForDashboard()

            page.reload()
            waitForApp()
            waitForDashboard()

            val second = AppPage(page.context().newPage(), baseUrl)
            second.open("#/extrato")
            second.ui.heading("Extrato").waitFor()
            assertThat(second.ui.hash()).isEqualTo("#/extrato")
            assertThat(second.storedToken()).isEqualTo(storedToken())
        }
    }

    // US1-9 (closing the browser = closing the context; no session restore, see Assumptions of the spec)
    @ParameterizedTest
    @MethodSource("browsers")
    fun `a new browser session needs a new login`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            open("#/entrar")
            waitForLogin()
            login(email)
            waitForDashboard()
        }
        withPage(browserName) {
            open("#/painel")
            waitForLogin()
            assertThat(ui.hash()).isEqualTo("#/entrar?voltar=%2Fpainel")
            assertThat(storedToken()).isNull()
        }
    }

    // Edge Case "várias abas"
    @ParameterizedTest
    @MethodSource("browsers")
    fun `logout in one tab takes the other tab to the login`(browserName: String) {
        val email = registered()
        withPage(browserName) {
            open("#/entrar")
            waitForLogin()
            login(email)
            waitForDashboard()
            val second = AppPage(page.context().newPage(), baseUrl)
            second.open("#/mercado")
            second.ui.heading("Mercado").waitFor()

            logout()

            second.waitForLogin()
            assertThat(second.ui.hash()).isEqualTo("#/entrar")
            assertThat(second.storedToken()).isNull()
            assertThat(second.headingCount("Mercado")).isZero()
        }
    }

    // FR-008
    @ParameterizedTest
    @MethodSource("browsers")
    fun `the token is never in localStorage, cookies or URLs`(browserName: String) {
        val email = registered()
        val urls = mutableListOf<String>()
        withPage(browserName) {
            page.context().onRequest { urls += it.url() }
            open("#/entrar")
            waitForLogin()
            login(email)
            waitForDashboard()
            page.reload()
            waitForApp()
            waitForDashboard()

            val token = checkNotNull(storedToken())
            val localStorage = page.evaluate("() => JSON.stringify(Object.assign({}, localStorage))") as String
            assertThat(localStorage).doesNotContain(token)
            assertThat(page.context().cookies()).allSatisfy {
                assertThat(it.value).doesNotContain(token)
            }
            assertThat(urls).isNotEmpty().allSatisfy { assertThat(it).doesNotContain(token) }
            assertThat(page.url()).doesNotContain(token)
        }
    }
}
