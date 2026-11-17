package com.kotlinbitcoins.web.core.navigation

import com.kotlinbitcoins.web.core.statement.FilterError
import com.kotlinbitcoins.web.core.statement.StatementFilter
import com.kotlinbitcoins.web.core.support.failureError
import com.kotlinbitcoins.web.core.support.successValue
import com.kotlinbitcoins.web.core.time.CalendarDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Table of contracts/ui-routes.md. */
class AppRouteTest {
    private val table: Map<AppRoute, String> =
        mapOf(
            AppRoute.Login() to "#/entrar",
            AppRoute.Login(returnTo = "/extrato?de=2026-10-01&ate=2026-11-09") to
                "#/entrar?voltar=%2Fextrato%3Fde%3D2026-10-01%26ate%3D2026-11-09",
            AppRoute.Register to "#/cadastro",
            AppRoute.Dashboard to "#/painel",
            AppRoute.Deposit to "#/depositar",
            AppRoute.Purchase to "#/comprar",
            AppRoute.Investments to "#/investimentos",
            AppRoute.Redemption to "#/resgatar",
            AppRoute.Statement() to "#/extrato",
            AppRoute.Statement(from = "2026-10-01", to = "2026-11-09") to "#/extrato?de=2026-10-01&ate=2026-11-09",
            AppRoute.Market to "#/mercado",
        )

    @Test
    fun `round trip for every route of the contract`() {
        table.forEach { (route, fragment) ->
            assertEquals(fragment, route.toFragment(), "toFragment of $route")
            assertEquals(route, AppRoute.parse(fragment), "parse of $fragment")
        }
    }

    @Test
    fun `parse accepts the fragment with or without the hash`() {
        assertEquals(AppRoute.Market, AppRoute.parse("/mercado"))
        assertEquals(AppRoute.Market, AppRoute.parse("#/mercado"))
    }

    @Test
    fun `empty or unknown fragment resolves to the dashboard`() {
        listOf("", "#", "#/", "/", "#/desconhecida", "#/painel/extra", "#painel", "https://evil.example").forEach {
            assertEquals(AppRoute.Dashboard, AppRoute.parse(it), "\"$it\"")
        }
    }

    @Test
    fun `statement with both dates`() {
        val route = AppRoute.parse("#/extrato?de=2026-10-01&ate=2026-11-09")
        assertEquals(AppRoute.Statement("2026-10-01", "2026-11-09"), route)
        val expected = StatementFilter.Range(date("2026-10-01"), date("2026-11-09"))
        assertEquals(expected, (route as AppRoute.Statement).toFilter().successValue())
    }

    @Test
    fun `statement with a single or invalid date keeps the raw input`() {
        val onlyFrom = AppRoute.parse("#/extrato?de=2026-10-01")
        assertEquals(AppRoute.Statement("2026-10-01", null), onlyFrom)
        assertEquals(FilterError.InvalidRange, (onlyFrom as AppRoute.Statement).toFilter().failureError())

        val invalid = AppRoute.parse("#/extrato?de=2026-02-30&ate=2026-11-09")
        assertEquals(AppRoute.Statement("2026-02-30", "2026-11-09"), invalid)
        assertEquals(FilterError.InvalidRange, (invalid as AppRoute.Statement).toFilter().failureError())
    }

    @Test
    fun `extra parameters are ignored`() {
        assertEquals(
            AppRoute.Statement("2026-10-01", "2026-11-09"),
            AppRoute.parse("#/extrato?x=1&de=2026-10-01&ate=2026-11-09&utm=abc"),
        )
        assertEquals(AppRoute.Market, AppRoute.parse("#/mercado?x=1"))
    }

    @Test
    fun `statement filter resolution`() {
        assertEquals(
            StatementFilter.Range(date("2026-10-01"), date("2026-11-09")),
            AppRoute.Statement("2026-10-01", "2026-11-09").toFilter().successValue(),
        )
        assertEquals(StatementFilter.Default, AppRoute.Statement(null, null).toFilter().successValue())
        assertEquals(FilterError.InvalidRange, AppRoute.Statement("2026-10-01", null).toFilter().failureError())
        assertEquals(FilterError.InvalidRange, AppRoute.Statement(null, "2026-11-09").toFilter().failureError())
        assertEquals(
            FilterError.InvalidRange,
            AppRoute.Statement("2026-11-09", "2026-10-01").toFilter().failureError(),
        )
        assertEquals(
            StatementFilter.Range(date("2026-11-09"), date("2026-11-09")),
            AppRoute.Statement("2026-11-09", "2026-11-09").toFilter().successValue(),
        )
    }

    @Test
    fun `statement filter from user input`() {
        assertEquals(
            StatementFilter.Range(date("2026-10-01"), date("2026-11-09")),
            StatementFilter.fromUserInput("01/10/2026", "09/11/2026").successValue(),
        )
        assertEquals(StatementFilter.Default, StatementFilter.fromUserInput("", " ").successValue())
        assertEquals(FilterError.InvalidRange, StatementFilter.fromUserInput("01/10/2026", "").failureError())
        assertEquals(FilterError.InvalidRange, StatementFilter.fromUserInput("31/02/2026", "09/11/2026").failureError())
    }

    @Test
    fun `range filter goes back to a statement route`() {
        val filter = StatementFilter.Range(date("2026-10-01"), date("2026-11-09"))
        assertEquals(AppRoute.Statement("2026-10-01", "2026-11-09"), AppRoute.Statement.of(filter))
        assertEquals(AppRoute.Statement(), AppRoute.Statement.of(StatementFilter.Default))
    }

    @Test
    fun `protection`() {
        assertFalse(AppRoute.Login().isProtected)
        assertFalse(AppRoute.Register.isProtected)
        listOf(
            AppRoute.Dashboard,
            AppRoute.Deposit,
            AppRoute.Purchase,
            AppRoute.Investments,
            AppRoute.Redemption,
            AppRoute.Statement(),
            AppRoute.Market,
        ).forEach { assertTrue(it.isProtected, "$it") }
    }

    private fun date(iso: String): CalendarDate = CalendarDate.parseIso(iso)!!
}
