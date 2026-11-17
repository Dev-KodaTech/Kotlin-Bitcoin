package com.kotlinbitcoins.web.e2e.layout

import com.kotlinbitcoins.web.e2e.support.E2eBase
import com.kotlinbitcoins.web.e2e.support.NoHorizontalScroll
import com.kotlinbitcoins.web.e2e.support.Viewports
import com.kotlinbitcoins.web.e2e.support.accountWithData
import com.kotlinbitcoins.web.e2e.support.h1Of
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * T218 (SC-008): the 9 screens at 640 × 400 with `deviceScaleFactor` 2, a 1280 px window at 200% zoom. No content
 * is lost (h1, data of the screen and the navigation of a 640 px window, the rail) and nothing scrolls horizontally.
 */
class Zoom200E2ETest : E2eBase() {
    @ParameterizedTest
    @MethodSource("browsers")
    fun `the 9 screens at 200 percent zoom keep their content without horizontal scroll`(browserName: String) {
        val viewport = Viewports.ZOOM_200
        val token = api.accountWithData()
        withPage(browserName, viewport) {
            visitAllScreens(token) { screen ->
                NoHorizontalScroll.assertOn(page)
                val box = checkNotNull(h1Of(screen).boundingBox()) { "h1 of ${screen.key}" }
                assertThat(box.x + box.width)
                    .describedAs("h1 of ${screen.key} inside 640 px")
                    .isLessThanOrEqualTo(viewport.width + 1.0)
                assertThat(screen.loaded(page)).describedAs("data of ${screen.key}").isTrue()
                if (screen.isProtected) assertNavigation(screen, viewport.width)
            }
        }
    }
}
