package com.kotlinbitcoins.web.e2e.layout

import com.kotlinbitcoins.web.e2e.auth.PASSWORD
import com.kotlinbitcoins.web.e2e.support.AppPage
import com.kotlinbitcoins.web.e2e.support.Axe
import com.kotlinbitcoins.web.e2e.support.E2eBase
import com.kotlinbitcoins.web.e2e.support.Screens
import com.kotlinbitcoins.web.e2e.support.Viewports
import com.kotlinbitcoins.web.e2e.support.buttonNamed
import com.kotlinbitcoins.web.e2e.support.injectSession
import com.kotlinbitcoins.web.e2e.support.waitForScreen
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.ColorScheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * Theme switch of the shell: "Sistema" follows `prefers-color-scheme`, "Claro" and "Escuro" win over it, the choice
 * survives a reload (kept in `localStorage` as `kb.theme`, also read by the boot screen) and "Sistema" removes it.
 * Sidebar (desktop), compact menu (phone) and navigation rail (tablet).
 */
class ThemeSwitchE2ETest : E2eBase() {
    @ParameterizedTest
    @MethodSource("browsers")
    fun `the sidebar switch applies, keeps and resets the theme`(browserName: String) {
        val token = api.login(registered(), PASSWORD)
        withPage(browserName) {
            page.emulateMedia(Page.EmulateMediaOptions().setColorScheme(ColorScheme.LIGHT))
            openDashboard(token)
            buttonNamed("Sistema, tema atual").waitFor()
            assertThat(isDark()).describedAs("Sistema with a light system").isFalse()

            ui.click(buttonNamed("Escuro"))
            buttonNamed("Escuro, tema atual").waitFor()
            assertThat(isDark()).describedAs("Escuro with a light system").isTrue()
            assertThat(storedTheme()).isEqualTo("dark")
            Axe.assertNoViolations(page)

            page.reload()
            waitForApp()
            waitForScreen(Screens.DASHBOARD)
            buttonNamed("Escuro, tema atual").waitFor()
            assertThat(isDark()).describedAs("Escuro after a reload").isTrue()
            assertThat(page.locator("html").getAttribute("data-theme")).isEqualTo("dark")

            page.emulateMedia(Page.EmulateMediaOptions().setColorScheme(ColorScheme.DARK))
            ui.click(buttonNamed("Claro"))
            buttonNamed("Claro, tema atual").waitFor()
            assertThat(isDark()).describedAs("Claro with a dark system").isFalse()
            assertThat(storedTheme()).isEqualTo("light")

            ui.click(buttonNamed("Sistema"))
            buttonNamed("Sistema, tema atual").waitFor()
            assertThat(isDark()).describedAs("Sistema with a dark system").isTrue()
            assertThat(storedTheme()).isNull()
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `on a phone the switch is in the menu`(browserName: String) {
        val token = api.login(registered(), PASSWORD)
        withPage(browserName, Viewports.PHONE) {
            page.emulateMedia(Page.EmulateMediaOptions().setColorScheme(ColorScheme.LIGHT))
            openDashboard(token)
            assertThat(isDark()).isFalse()

            ui.click(buttonNamed("Menu"))
            ui.click(buttonNamed("Escuro"))
            buttonNamed("Escuro, tema atual").waitFor()
            assertThat(storedTheme()).isEqualTo("dark")
            ui.press("Escape")
            assertThat(isDark()).describedAs("Escuro chosen in the menu").isTrue()
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    fun `on the navigation rail the options are icons named by their theme`(browserName: String) {
        val token = api.login(registered(), PASSWORD)
        withPage(browserName, Viewports.TABLET) {
            page.emulateMedia(Page.EmulateMediaOptions().setColorScheme(ColorScheme.DARK))
            openDashboard(token)
            buttonNamed("Sistema, tema atual").waitFor()
            assertThat(isDark()).isTrue()

            ui.click(buttonNamed("Claro"))
            buttonNamed("Claro, tema atual").waitFor()
            assertThat(isDark()).describedAs("Claro with a dark system").isFalse()
            assertThat(storedTheme()).isEqualTo("light")
        }
    }

    private fun registered(): String = api.uniqueEmail().also { api.register("Ana Lima", it, PASSWORD) }

    private fun AppPage.openDashboard(token: String) {
        open(Screens.LOGIN.fragment)
        waitForApp()
        injectSession(token)
        page.evaluate("f => { location.hash = f }", Screens.DASHBOARD.fragment)
        page.reload()
        waitForApp()
        waitForScreen(Screens.DASHBOARD)
    }

    private fun AppPage.storedTheme(): String? = page.evaluate("() => localStorage.getItem('kb.theme')") as String?

    /** The ground at the top left corner of the page (the shell's frame, or the shell itself on a phone). */
    private fun AppPage.isDark(): Boolean {
        page.waitForTimeout(THEME_SETTLE_MS)
        val image = ImageIO.read(ByteArrayInputStream(page.screenshot()))
        val rgb = image.getRGB(CORNER, CORNER)
        val red = rgb shr RED_SHIFT and CHANNEL
        val green = rgb shr GREEN_SHIFT and CHANNEL
        val blue = rgb and CHANNEL
        return (red + green + blue) / CHANNELS < DARK_BELOW
    }

    private companion object {
        const val THEME_SETTLE_MS = 400.0
        const val CORNER = 2
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val CHANNEL = 0xFF
        const val CHANNELS = 3
        const val DARK_BELOW = 96
    }
}
