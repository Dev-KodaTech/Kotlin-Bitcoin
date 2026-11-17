package com.kotlinbitcoins.web.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class ReturnDestinationTest {
    @Test
    fun `accepts protected routes of the app`() {
        assertEquals(
            AppRoute.Statement("2026-10-01", "2026-11-09"),
            ReturnDestination.resolve("/extrato?de=2026-10-01&ate=2026-11-09"),
        )
        assertEquals(AppRoute.Investments, ReturnDestination.resolve("/investimentos"))
        assertEquals(AppRoute.Market, ReturnDestination.resolve("/mercado"))
    }

    @Test
    fun `anything else resolves to the dashboard`() {
        listOf(
            "https://evil.example",
            "//evil.example",
            "javascript:alert(1)",
            "/entrar",
            "/entrar?voltar=%2Fmercado",
            "/cadastro",
            "../",
            "",
            null,
            "%2Fextrato",
            "%252Fextrato",
            "/desconhecida",
            "/painel/../../evil",
        ).forEach { raw ->
            assertEquals(AppRoute.Dashboard, ReturnDestination.resolve(raw), "\"$raw\"")
        }
    }

    @Test
    fun `round trip through the login route`() {
        val login = AppRoute.Login(returnTo = "/extrato?de=2026-10-01&ate=2026-11-09")
        val parsed = AppRoute.parse(login.toFragment()) as AppRoute.Login
        assertEquals(AppRoute.Statement("2026-10-01", "2026-11-09"), ReturnDestination.resolve(parsed.returnTo))
    }

    @Test
    fun `double encoded login parameter is refused`() {
        val parsed = AppRoute.parse("#/entrar?voltar=%252Fmercado") as AppRoute.Login
        assertEquals("%2Fmercado", parsed.returnTo)
        assertEquals(AppRoute.Dashboard, ReturnDestination.resolve(parsed.returnTo))
    }
}
