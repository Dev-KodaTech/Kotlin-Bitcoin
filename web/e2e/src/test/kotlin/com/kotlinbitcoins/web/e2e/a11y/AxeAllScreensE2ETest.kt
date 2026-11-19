package com.kotlinbitcoins.web.e2e.a11y

import com.kotlinbitcoins.web.e2e.operations.confirmButton
import com.kotlinbitcoins.web.e2e.operations.depositButton
import com.kotlinbitcoins.web.e2e.operations.redemptionConfirmButton
import com.kotlinbitcoins.web.e2e.operations.reviewPurchase
import com.kotlinbitcoins.web.e2e.operations.reviewRedemption
import com.kotlinbitcoins.web.e2e.operations.typeAmount
import com.kotlinbitcoins.web.e2e.operations.waitForDepositResult
import com.kotlinbitcoins.web.e2e.operations.waitForPurchaseResult
import com.kotlinbitcoins.web.e2e.operations.waitForRedemptionResult
import com.kotlinbitcoins.web.e2e.support.ApiRouter
import com.kotlinbitcoins.web.e2e.support.AppPage
import com.kotlinbitcoins.web.e2e.support.Axe
import com.kotlinbitcoins.web.e2e.support.E2eBase
import com.kotlinbitcoins.web.e2e.support.Screen
import com.kotlinbitcoins.web.e2e.support.Screens
import com.kotlinbitcoins.web.e2e.support.accountWithData
import com.kotlinbitcoins.web.e2e.support.buttonNamed
import com.kotlinbitcoins.web.e2e.support.goTo
import com.kotlinbitcoins.web.e2e.support.injectSession
import com.kotlinbitcoins.web.e2e.support.waitForScreen
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.ColorScheme
import com.microsoft.playwright.options.ReducedMotion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.util.regex.Pattern
import javax.imageio.ImageIO

/**
 * T219 (FR-045, W-17): axe (WCAG 2.1 A/AA) on every screen in each of its states (loading, empty, error, loaded,
 * review, result), in the light and in the dark color scheme, with reduced motion. With reduced motion nothing
 * animates: while a screen is loading (its requests held by [ApiRouter]) consecutive captures are identical, which
 * rules out a navigation transition, an animated progress indicator and animated values. Violations are collected
 * per screen and state and reported together.
 */
class AxeAllScreensE2ETest : E2eBase() {
    fun schemes(): List<Arguments> =
        browsers().flatMap { browser ->
            listOf(ColorScheme.LIGHT, ColorScheme.DARK).map { Arguments.of(browser, Named.of(it.name, it)) }
        }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("schemes")
    fun `every screen and state has no axe violation and nothing moves with reduced motion`(
        browserName: String,
        scheme: ColorScheme,
    ) {
        val token = api.accountWithData()
        withPage(browserName) {
            page.emulateMedia(Page.EmulateMediaOptions().setColorScheme(scheme).setReducedMotion(ReducedMotion.REDUCE))
            val run = Run(this, ApiRouter(page))
            run.publicScreens()
            injectSession(token)
            run.dataScreens()
            run.deposit()
            run.purchaseReviewAndResult()
            run.redemptionReviewAndResult()

            assertThat(run.moving).describedAs("animated states with reduced motion").isEmpty()
            assertThat(run.violations).describedAs("axe WCAG 2.1 A/AA violations ($scheme)").isEmpty()
            assertThat(run.checked).describedAs("screen/state pairs checked").hasSizeGreaterThanOrEqualTo(MIN_CHECKS)
        }
    }

    private class Run(
        private val app: AppPage,
        private val router: ApiRouter,
    ) {
        val violations = mutableListOf<String>()
        val moving = mutableListOf<String>()
        val checked = mutableListOf<String>()
        private val page = app.page

        fun publicScreens() {
            app.open(Screens.LOGIN.fragment)
            app.waitForScreen(Screens.LOGIN)
            axe(Screens.LOGIN, "carregado")
            // The login does not validate locally: the VALIDATION_ERROR of the API comes as the error summary.
            app.ui.click(app.buttonNamed("Entrar").last())
            page.getByRole(AriaRole.ALERT).first().waitFor()
            axe(Screens.LOGIN, "erro")

            app.goTo(Screens.REGISTER)
            axe(Screens.REGISTER, "carregado")
            app.ui.click(app.buttonNamed("Criar conta").last())
            waitForInvalidField()
            axe(Screens.REGISTER, "erro")
        }

        /** Error, loading, loaded and (where it exists) empty state of every screen that reads data. */
        fun dataScreens() {
            val withData =
                listOf(Screens.DASHBOARD, Screens.PURCHASE, Screens.INVESTMENTS, Screens.REDEMPTION) +
                    listOf(Screens.STATEMENT, Screens.MARKET)
            withData.forEachIndexed { index, screen ->
                router.error = ApiRouter.ANY_GET
                if (index == 0) {
                    // The session was just written: start the app on the dashboard.
                    page.evaluate("f => { location.hash = f }", screen.fragment)
                    page.reload()
                    app.waitForApp()
                } else {
                    app.goTo(screen, withData = false)
                }
                app.waitForScreen(screen, withData = false)
                retryButton().waitFor()
                axe(screen, "erro")

                router.error = null
                router.hold = ApiRouter.ANY_GET
                // Every section that failed has its own button (the market has two): each click leaves one less.
                repeat(app.buttonNamed("Tentar de novo").count()) { app.ui.click(retryButton()) }
                page.waitForCondition { isBusy() }
                assertStill(screen, "carregando")
                axe(screen, "carregando")
                router.release()

                reopen(screen)
                axe(screen, "carregado")

                EMPTY[screen]?.let { (fixtures, text) ->
                    router.fixtures.putAll(fixtures)
                    reopen(screen, withData = false)
                    page.getByText(text).first().waitFor()
                    axe(screen, "vazio")
                    router.fixtures.clear()
                }
            }
        }

        /** The form is what the deposit is for: its error and loading states are those of the submission. */
        fun deposit() {
            app.goTo(Screens.DEPOSIT)
            axe(Screens.DEPOSIT, "carregado")
            router.error = ApiRouter.post("/me/deposits")
            app.typeAmount("10")
            app.ui.click(app.depositButton())
            page.getByText(SERVICE_UNAVAILABLE).first().waitFor()
            axe(Screens.DEPOSIT, "erro")

            router.error = null
            router.hold = ApiRouter.post("/me/deposits")
            // The 503 replaces the form with the error block: "Tentar de novo" brings the form back with the amount.
            app.ui.click(retryButton())
            app.ui.click(app.depositButton())
            page.waitForCondition { isBusy() }
            assertStill(Screens.DEPOSIT, "carregando")
            axe(Screens.DEPOSIT, "carregando")
            router.release()
            app.waitForDepositResult("R$ 10,00")
            axe(Screens.DEPOSIT, "resultado")
        }

        fun purchaseReviewAndResult() {
            app.goTo(Screens.PURCHASE)
            app.reviewPurchase("10")
            axe(Screens.PURCHASE, "revisão")
            app.ui.click(app.confirmButton())
            app.waitForPurchaseResult()
            axe(Screens.PURCHASE, "resultado")
        }

        fun redemptionReviewAndResult() {
            app.goTo(Screens.REDEMPTION)
            app.reviewRedemption("10")
            axe(Screens.REDEMPTION, "revisão")
            app.ui.click(app.redemptionConfirmButton())
            app.waitForRedemptionResult()
            axe(Screens.REDEMPTION, "resultado")
        }

        private fun axe(
            screen: Screen,
            state: String,
        ) {
            checked += "${screen.key}/$state"
            violations += Axe.violations(page).map { "${screen.key}/$state: $it" }
        }

        /**
         * Opens [screen] in a fresh app (reload), so it loads again: going back to it by the URL would pop to its
         * previous entry, whose ViewModel keeps the state it had.
         */
        private fun reopen(
            screen: Screen,
            withData: Boolean = true,
        ) {
            page.evaluate("f => { location.hash = f }", screen.fragment)
            page.reload()
            app.waitForApp()
            app.waitForScreen(screen, withData)
        }

        private fun retryButton() = app.buttonNamed("Tentar de novo").first()

        private fun isBusy(): Boolean = Screens.isLoading(page) || page.getByText("Processando…").count() > 0

        private fun waitForInvalidField() {
            page.waitForCondition { app.ui.focused()?.isInvalid == true }
        }

        /** Three captures [STILL_INTERVAL_MS] apart must be the same image. */
        private fun assertStill(
            screen: Screen,
            state: String,
        ) {
            val first = pixels(app.screenshotOutsideCsp())
            repeat(STILL_CAPTURES) {
                page.waitForTimeout(STILL_INTERVAL_MS)
                if (!pixels(app.screenshotOutsideCsp()).contentEquals(first)) {
                    moving += "${screen.key}/$state"
                    return
                }
            }
        }

        private fun pixels(png: ByteArray): IntArray {
            val image = ImageIO.read(ByteArrayInputStream(png))
            return image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        }
    }

    private companion object {
        const val SERVICE_UNAVAILABLE = "Serviço temporariamente indisponível."
        const val STILL_CAPTURES = 2
        const val STILL_INTERVAL_MS = 250.0

        /** 2 public screens × 2 states, 6 data screens × 3, 5 empty states, deposit × 4, 2 × (review, result). */
        const val MIN_CHECKS = 4 + 18 + 5 + 4 + 4

        val EMPTY_POSITION = mapOf("/me/investments" to "me-investments-200-empty.json")
        val EMPTY: Map<Screen, Pair<Map<String, String>, Pattern>> =
            mapOf(
                Screens.DASHBOARD to (EMPTY_POSITION to Pattern.compile("^Você ainda não tem investimentos")),
                Screens.INVESTMENTS to (EMPTY_POSITION to Pattern.compile("^Você não tem investimentos ativos")),
                Screens.REDEMPTION to (EMPTY_POSITION to Pattern.compile("^Você não tem investimentos para resgatar")),
                Screens.STATEMENT to
                    (mapOf("/me/statement" to "me-statement-200-empty.json") to Pattern.compile("^Nenhum lançamento")),
                Screens.MARKET to
                    (
                        mapOf("/quotes/history" to "quotes-history-200-empty.json") to
                            Pattern.compile("^Sem dados de cotação")
                    ),
            )
    }
}
