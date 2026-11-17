package com.kotlinbitcoins.web.e2e.support

import com.kotlinbitcoins.web.e2e.dashboard.fixturesDir
import com.microsoft.playwright.Page
import com.microsoft.playwright.Request
import com.microsoft.playwright.Route
import java.net.URI
import java.nio.file.Files

/**
 * One `page.route` over the API that puts a screen in a chosen state: [error] answers the matching requests with the
 * SERVICE_UNAVAILABLE fixture (503), [hold] keeps them pending until [release] (loading state), and [fixtures] answers
 * a path with a success fixture (e.g. the empty position). Everything else goes to the real API.
 */
class ApiRouter(
    page: Page,
) {
    var error: ((Request) -> Boolean)? = null
    var hold: ((Request) -> Boolean)? = null
    val fixtures = mutableMapOf<String, String>()
    private val held = mutableListOf<Route>()
    private val problem = Files.readString(fixturesDir().resolve("problem/SERVICE_UNAVAILABLE.json"))

    init {
        page.route("**/api/v1/**") { route ->
            val request = route.request()
            val path = URI.create(request.url()).path
            val fixture = fixtures.entries.firstOrNull { path.endsWith(it.key) }?.value
            when {
                error?.invoke(request) == true -> route.fulfill(problemResponse())
                hold?.invoke(request) == true -> held += route
                fixture != null && request.method() == "GET" -> route.fulfill(fixtureResponse(fixture))
                else -> route.resume()
            }
        }
    }

    /** Stops holding and lets every held request go to the API. */
    fun release() {
        hold = null
        held.toList().forEach(Route::resume)
        held.clear()
    }

    fun reset() {
        error = null
        fixtures.clear()
        release()
    }

    private fun problemResponse(): Route.FulfillOptions =
        Route
            .FulfillOptions()
            .setStatus(HTTP_SERVICE_UNAVAILABLE)
            .setContentType("application/problem+json")
            .setBody(problem)

    private fun fixtureResponse(file: String): Route.FulfillOptions =
        Route
            .FulfillOptions()
            .setStatus(HTTP_OK)
            .setContentType("application/json")
            .setBody(Files.readString(fixturesDir().resolve("success/$file")))

    companion object {
        val ANY_GET: (Request) -> Boolean = { it.method() == "GET" }

        fun post(pathSuffix: String): (Request) -> Boolean =
            { it.method() == "POST" && URI.create(it.url()).path.endsWith(pathSuffix) }

        private const val HTTP_OK = 200
        private const val HTTP_SERVICE_UNAVAILABLE = 503
    }
}
