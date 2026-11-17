package com.kotlinbitcoins.web.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class HistorySyncTest {
    private val dashboard = HistoryEntry("1", "#/painel")
    private val statement = HistoryEntry("2", "#/extrato")
    private val filtered = HistoryEntry("3", "#/extrato?de=2026-10-01&ate=2026-11-09")
    private val login = HistoryEntry("4", "#/entrar")

    @Test
    fun `the first stack replaces the URL, so the fragment is canonical without a new entry`() {
        assertEquals(HistoryAction.Replace("#/painel"), HistorySync.action(emptyList(), listOf(dashboard), false))
    }

    @Test
    fun `an empty stack does nothing`() {
        assertEquals(HistoryAction.None, HistorySync.action(listOf(dashboard), emptyList(), false))
    }

    @Test
    fun `a new entry on top pushes`() {
        assertEquals(
            HistoryAction.Push("#/extrato"),
            HistorySync.action(listOf(dashboard), listOf(dashboard, statement), false),
        )
    }

    @Test
    fun `replacing the top entry replaces, as the filter, login and gate redirects do`() {
        assertEquals(
            HistoryAction.Replace("#/extrato?de=2026-10-01&ate=2026-11-09"),
            HistorySync.action(listOf(dashboard, statement), listOf(dashboard, filtered), false),
        )
        // Logout clears the stack.
        assertEquals(
            HistoryAction.Replace("#/entrar"),
            HistorySync.action(listOf(dashboard, statement), listOf(login), false),
        )
    }

    @Test
    fun `an unchanged stack does nothing`() {
        assertEquals(HistoryAction.None, HistorySync.action(listOf(dashboard), listOf(dashboard), false))
    }

    @Test
    fun `a change that answers the browser never pushes`() {
        assertEquals(
            HistoryAction.Replace("#/extrato"),
            HistorySync.action(listOf(dashboard), listOf(dashboard, statement), true),
        )
        assertEquals(
            HistoryAction.Replace("#/painel"),
            HistorySync.action(listOf(dashboard, statement), listOf(dashboard), true),
        )
    }

    @Test
    fun `the browser back to the entry below pops`() {
        assertEquals(
            BrowserChange.Pop,
            HistorySync.onBrowserChange(listOf(AppRoute.Dashboard, AppRoute.Statement()), AppRoute.Dashboard),
        )
    }

    @Test
    fun `the fragment of the current route does nothing`() {
        assertEquals(
            BrowserChange.None,
            HistorySync.onBrowserChange(listOf(AppRoute.Dashboard), AppRoute.Dashboard),
        )
    }

    @Test
    fun `forward, a link or the address bar navigate`() {
        val target = AppRoute.Statement("2026-10-01", "2026-11-09")
        assertEquals(
            BrowserChange.Navigate(target),
            HistorySync.onBrowserChange(listOf(AppRoute.Dashboard, AppRoute.Statement()), target),
        )
        assertEquals(BrowserChange.Navigate(AppRoute.Market), HistorySync.onBrowserChange(emptyList(), AppRoute.Market))
    }
}
