package com.kotlinbitcoins.web.e2e.layout

import com.kotlinbitcoins.web.e2e.support.E2eBase
import com.kotlinbitcoins.web.e2e.support.NoHorizontalScroll
import com.kotlinbitcoins.web.e2e.support.Viewport
import com.kotlinbitcoins.web.e2e.support.Viewports
import com.kotlinbitcoins.web.e2e.support.accountWithData
import com.kotlinbitcoins.web.e2e.support.h1Of
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * T217 (FR-009, FR-044, SC-008): the 9 screens with data at 320, 375, 768, 1280 and 1920 px have no horizontal
 * scroll (document and mirror nodes inside the viewport), the h1 is in view, and the protected screens show the
 * navigation of the window class (menu < 600, rail 600–839, drawer ≥ 840) with the current destination named.
 */
class ResponsiveLayoutE2ETest : E2eBase() {
    fun widths(): List<Arguments> =
        browsers().flatMap { browser -> WIDTHS.map { Arguments.of(browser, Named.of(it.name, it)) } }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("widths")
    fun `the 9 screens fit the width and show the navigation of their class`(
        browserName: String,
        viewport: Viewport,
    ) {
        val token = api.accountWithData()
        withPage(browserName, viewport) {
            visitAllScreens(token) { screen ->
                NoHorizontalScroll.assertOn(page)
                val box = checkNotNull(h1Of(screen).boundingBox()) { "h1 of ${screen.key}" }
                assertThat(box.x).describedAs("h1 of ${screen.key} starts in view").isGreaterThanOrEqualTo(0.0)
                assertThat(box.x + box.width)
                    .describedAs("h1 of ${screen.key} ends in view")
                    .isLessThanOrEqualTo(viewport.width + 1.0)
                if (screen.isProtected) assertNavigation(screen, viewport.width)
            }
        }
    }

    private companion object {
        val WIDTHS = listOf(Viewports.PHONE_SMALL, Viewports.PHONE, Viewports.TABLET, Viewports.DESKTOP, Viewports.WIDE)
    }
}
