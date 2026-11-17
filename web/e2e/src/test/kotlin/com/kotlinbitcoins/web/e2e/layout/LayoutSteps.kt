package com.kotlinbitcoins.web.e2e.layout

import com.kotlinbitcoins.web.e2e.support.AppPage
import com.kotlinbitcoins.web.e2e.support.Screen
import com.kotlinbitcoins.web.e2e.support.Screens
import com.kotlinbitcoins.web.e2e.support.buttonNamed
import com.kotlinbitcoins.web.e2e.support.goTo
import com.kotlinbitcoins.web.e2e.support.injectSession
import com.kotlinbitcoins.web.e2e.support.waitForScreen
import org.assertj.core.api.Assertions.assertThat

/**
 * Visits the 9 screens in one tab: the public ones without a session, then the session of [token] is written and
 * the protected ones are opened by fragment, each one with its data. [check] runs on each screen once it is loaded.
 */
fun AppPage.visitAllScreens(
    token: String,
    check: AppPage.(Screen) -> Unit,
) {
    open(Screens.LOGIN.fragment)
    Screens.PUBLIC.forEach { screen ->
        goTo(screen)
        check(screen)
    }
    injectSession(token)
    // A fragment change keeps the document: reload so the app starts with the stored session.
    page.evaluate("f => { location.hash = f }", Screens.DASHBOARD.fragment)
    page.reload()
    waitForApp()
    waitForScreen(Screens.DASHBOARD)
    Screens.PROTECTED.forEach { screen ->
        goTo(screen)
        check(screen)
    }
}

/** Navigation of the shell by window class (W-14, FR-009): modal menu, rail or permanent drawer. */
enum class NavigationKind { MENU, RAIL, DRAWER }

fun navigationFor(width: Int): NavigationKind =
    when {
        width < COMPACT_MAX -> NavigationKind.MENU
        width < MEDIUM_MAX -> NavigationKind.RAIL
        else -> NavigationKind.DRAWER
    }

/**
 * FR-009 / FR-044: the shell shows the navigation of its window class, with every destination and "Sair", and the
 * current destination named "…, página atual". In the compact class the menu is opened by "Menu", closed with
 * Escape, opened again and closed by choosing the current destination (which must not leave it open).
 */
fun AppPage.assertNavigation(
    screen: Screen,
    width: Int,
) {
    val kind = navigationFor(width)
    val menu = buttonNamed(MENU)
    val rail = page.locator("[aria-label='$RAIL_NAME']")
    when (kind) {
        NavigationKind.MENU -> {
            assertThat(menu.count()).describedAs("'Menu' at $width px").isEqualTo(1)
            assertThat(rail.count()).describedAs("rail at $width px").isZero()
            ui.click(menu)
            buttonNamed("${screen.navLabel}, $CURRENT").waitFor()
            assertDestinations(screen)
            ui.press("Escape")
            waitForMenuClosed(screen)
            ui.click(menu)
            ui.click(buttonNamed("${screen.navLabel}, $CURRENT"))
            waitForMenuClosed(screen)
            waitForScreen(screen)
        }

        NavigationKind.RAIL -> {
            assertThat(menu.count()).describedAs("'Menu' at $width px").isZero()
            assertThat(rail.count()).describedAs("rail at $width px").isEqualTo(1)
            assertDestinations(screen)
        }

        NavigationKind.DRAWER -> {
            assertThat(menu.count()).describedAs("'Menu' at $width px").isZero()
            assertThat(rail.count()).describedAs("rail at $width px").isZero()
            assertDestinations(screen)
        }
    }
}

/** A closed modal drawer stays in the mirror with empty boxes: closed means the current item has no width. */
private fun AppPage.waitForMenuClosed(screen: Screen) {
    page.waitForCondition {
        val item = buttonNamed("${screen.navLabel}, $CURRENT")
        item.count() == 0 || (item.first().boundingBox()?.width ?: 0.0) == 0.0
    }
}

private fun AppPage.assertDestinations(screen: Screen) {
    assertThat(buttonNamed("${screen.navLabel}, $CURRENT").count())
        .describedAs("current destination of ${screen.key}")
        .isEqualTo(1)
    Screens.PROTECTED.filter { it != screen }.forEach { other ->
        assertThat(buttonNamed(checkNotNull(other.navLabel)).count())
            .describedAs("menu item ${other.navLabel} on ${screen.key}")
            .isGreaterThanOrEqualTo(1)
    }
    assertThat(buttonNamed("Sair").count()).describedAs("'Sair' on ${screen.key}").isEqualTo(1)
}

private const val COMPACT_MAX = 600
private const val MEDIUM_MAX = 840
private const val MENU = "Menu"
private const val RAIL_NAME = "Navegação principal"
private const val CURRENT = "página atual"
