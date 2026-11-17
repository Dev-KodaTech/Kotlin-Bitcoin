package com.kotlinbitcoins.web.data.support

import com.kotlinbitcoins.web.data.fixtures.ContractFixtures
import io.ktor.http.HttpStatusCode

/** Body and status of the fixture of a manifest scenario. */
fun fixture(scenario: String): Pair<String, HttpStatusCode> {
    val fixture = ContractFixtures.byScenario(scenario)
    return fixture.body to HttpStatusCode.fromValue(fixture.entry.status)
}

/** A mock API answering every request with the fixture of [scenario] (success as JSON, error as a Problem). */
fun fixtureApi(scenario: String): MockApi {
    val (body, status) = fixture(scenario)
    return MockApi { if (status.value < 300) respondJson(body, status) else respondProblem(body, status) }
}
