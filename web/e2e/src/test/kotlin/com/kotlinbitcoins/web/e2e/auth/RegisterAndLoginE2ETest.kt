package com.kotlinbitcoins.web.e2e.auth

import com.kotlinbitcoins.web.e2e.support.Axe
import com.kotlinbitcoins.web.e2e.support.E2eBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** US1: registration with automatic login, login, logout and the guard of protected pages (T108). */
class RegisterAndLoginE2ETest : E2eBase() {
    // US1-1
    @ParameterizedTest
    @MethodSource("browsers")
    fun `registration logs in automatically and opens the dashboard`(browserName: String) {
        withPage(browserName) {
            open("#/cadastro")
            ui.heading("Criar conta").waitFor()
            Axe.assertNoViolations(page)

            register("Ana Souza", api.uniqueEmail())

            waitForDashboard()
            assertThat(storedToken()).isNotNull()
        }
    }

    // US1-2
    @ParameterizedTest
    @MethodSource("browsers")
    fun `invalid fields show their own errors, focus the first one and send nothing`(browserName: String) {
        withPage(browserName) {
            val accounts = ApiRequests(page, "/accounts")
            open("#/cadastro")
            ui.heading("Criar conta").waitFor()

            register(name = "   ", email = "ana-sem-arroba", password = "1234567")

            val first = ui.focused()
            assertThat(first?.label).isEqualTo("Nome")
            assertThat(first?.isInvalid).isTrue()
            assertThat(first?.description).isEqualTo("Informe seu nome.")
            ui.press("Tab")
            assertThat(ui.focused()?.label).isEqualTo("E-mail")
            assertThat(ui.focused()?.description).isEqualTo("Informe um e-mail válido.")
            ui.press("Tab")
            assertThat(ui.focused()?.label).isEqualTo("Senha")
            assertThat(ui.focused()?.description).isEqualTo("A senha deve ter pelo menos 8 caracteres.")
            assertThat(hasText("Revise os campos destacados.")).isTrue()
            assertThat(accounts.count).describedAs("POST /accounts").isZero()
            Axe.assertNoViolations(page)
        }
    }

    // US1-3
    @ParameterizedTest
    @MethodSource("browsers")
    fun `duplicate email keeps name and email, clears the password and offers the login`(browserName: String) {
        val email = api.uniqueEmail()
        assertThat(api.register("Ana", email, PASSWORD).status).isEqualTo(HTTP_CREATED)
        withPage(browserName) {
            open("#/cadastro")
            ui.heading("Criar conta").waitFor()

            register("Ana Souza", email)

            page.getByText("Este e-mail já está cadastrado.").first().waitFor()
            assertThat(ui.focused()?.label).isEqualTo("E-mail")
            assertThat(ui.focused()?.description).isEqualTo("Este e-mail já está cadastrado.")
            assertThat(ui.button("Entrar").count()).isGreaterThanOrEqualTo(1)
            Axe.assertNoViolations(page)

            // Only the email changes: the name was kept and the password was cleared.
            fillField("E-mail", api.uniqueEmail())
            ui.click(ui.button("Criar conta"))
            assertThat(ui.focused()?.label).isEqualTo("Senha")
            assertThat(ui.focused()?.description).isEqualTo("A senha deve ter pelo menos 8 caracteres.")
            assertThat(hasText("Informe seu nome.")).isFalse()

            // Typing removes the error summary and the layout moves up; submit with Enter (ImeAction.Done).
            fillField("Senha", PASSWORD)
            ui.press("Enter")
            waitForDashboard()
        }
        withPage(browserName) {
            open("#/cadastro")
            ui.heading("Criar conta").waitFor()
            register("Ana Souza", email)
            page.getByText("Este e-mail já está cadastrado.").first().waitFor()
            ui.click(ui.button("Entrar").first())
            waitForLogin()
            assertThat(ui.hash()).isEqualTo("#/entrar")
        }
    }

    // US1-4
    @ParameterizedTest
    @MethodSource("browsers")
    fun `unknown email and wrong password get the same generic message`(browserName: String) {
        val email = api.uniqueEmail()
        api.register("Ana", email, PASSWORD)
        withPage(browserName) {
            open("#/entrar")
            waitForLogin()
            Axe.assertNoViolations(page)

            login(api.uniqueEmail())
            page.getByText(INVALID_CREDENTIALS).first().waitFor()
            waitForAnnouncement(INVALID_CREDENTIALS)
            Axe.assertNoViolations(page)

            page.evaluate("() => document.getElementById('live-assertive').textContent = ''")
            login(email, "senha-errada-123")
            waitForAnnouncement(INVALID_CREDENTIALS)
            assertThat(ui.hash()).startsWith("#/entrar")
            assertThat(storedToken()).isNull()
        }
    }

    // US1-7
    @ParameterizedTest
    @MethodSource("browsers")
    fun `logout goes to the login and back does not show account pages`(browserName: String) {
        val email = api.uniqueEmail()
        api.register("Ana", email, PASSWORD)
        withPage(browserName) {
            open("#/entrar")
            waitForLogin()
            login(email)
            waitForDashboard()

            logout()
            assertThat(storedToken()).isNull()
            assertThat(ui.hash()).isEqualTo("#/entrar")

            page.goBack()
            page.waitForTimeout(SETTLE_MS)
            waitForLogin()
            assertThat(headingCount("Painel")).isZero()
            assertThat(page.title()).isEqualTo(LOGIN_TITLE)
        }
    }

    // US1-8
    @ParameterizedTest
    @MethodSource("browsers")
    fun `a protected page opened without session goes to the login and back to it`(browserName: String) {
        val email = api.uniqueEmail()
        api.register("Ana", email, PASSWORD)
        withPage(browserName) {
            open("#/investimentos")
            waitForLogin()
            assertThat(ui.hash()).isEqualTo("#/entrar?voltar=%2Finvestimentos")
            assertThat(headingCount("Meus investimentos")).isZero()

            login(email)

            ui.waitForHash("#/investimentos")
            ui.heading("Meus investimentos").waitFor()
            // Login → destination replaces the login entry.
            page.goBack()
            page.waitForTimeout(SETTLE_MS)
            assertThat(ui.hash()).doesNotStartWith("#/entrar")
        }
    }

    // Edge Case: account created but the automatic login fails.
    @ParameterizedTest
    @MethodSource("browsers")
    fun `account created but automatic login failed goes to the login with its message`(browserName: String) {
        val email = api.uniqueEmail()
        withPage(browserName) {
            open("#/cadastro")
            ui.heading("Criar conta").waitFor()
            page.route("**/api/v1/auth/login") { it.abort() }

            register("Ana Souza", email)

            waitForLogin()
            page.getByText(ACCOUNT_CREATED).first().waitFor()
            assertThat(ui.hash()).isEqualTo("#/entrar")
            Axe.assertNoViolations(page)

            page.unroute("**/api/v1/auth/login")
            login(email)
            waitForDashboard()
        }
    }

    private companion object {
        const val HTTP_CREATED = 201
        const val SETTLE_MS = 800.0
    }
}
