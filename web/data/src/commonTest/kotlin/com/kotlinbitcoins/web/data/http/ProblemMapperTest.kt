package com.kotlinbitcoins.web.data.http

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.FieldError
import com.kotlinbitcoins.web.data.fixtures.ContractFixtures
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Table of contracts/error-messages.md; the fixtures are the ones validated against the contract (W-12). */
class ProblemMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val expected: Map<String, ApiError> =
        mapOf(
            "VALIDATION_ERROR" to ApiError.Validation(listOf(FieldError("email", "E-mail em formato inválido."))),
            "INVALID_AMOUNT" to ApiError.InvalidAmount,
            "INVALID_CREDENTIALS" to ApiError.InvalidCredentials,
            "UNAUTHORIZED" to ApiError.Unauthorized,
            "NOT_FOUND" to ApiError.Unexpected,
            "EMAIL_ALREADY_IN_USE" to ApiError.EmailAlreadyInUse,
            "INSUFFICIENT_BALANCE" to ApiError.InsufficientBalance,
            "REDEMPTION_EXCEEDS_AVAILABLE" to ApiError.RedemptionExceedsAvailable,
            "INVALID_DATE_RANGE" to ApiError.InvalidDateRange,
            "QUOTE_UNAVAILABLE" to ApiError.QuoteUnavailable,
            "SERVICE_UNAVAILABLE" to ApiError.ServiceUnavailable,
            "INTERNAL_ERROR" to ApiError.Unexpected,
        )

    private fun problem(code: String): ProblemDto =
        json.decodeFromString(ProblemDto.serializer(), ContractFixtures.byFile("problem/$code.json"))

    @Test
    fun everyProblemFixtureMapsToTheErrorOfTheTable() {
        expected.forEach { (code, error) -> assertEquals(error, ProblemMapper.map(problem(code)), code) }
    }

    @Test
    fun theTestedCodesAreExactlyTheProblemFixtures() {
        val fixtureCodes =
            ContractFixtures.paths
                .filter { it.startsWith("problem/") }
                .map { it.removePrefix("problem/").removeSuffix(".json") }
                .toSet()
        assertEquals(fixtureCodes, expected.keys)
    }

    @Test
    fun unknownOrMissingCodeIsUnexpected() {
        assertEquals(ApiError.Unexpected, ProblemMapper.map(ProblemDto(code = "SOMETHING_NEW")))
        assertEquals(ApiError.Unexpected, ProblemMapper.map(ProblemDto(code = null)))
    }

    @Test
    fun validationKeepsTheFieldErrors() {
        val dto =
            json.decodeFromString(
                ProblemDto.serializer(),
                """{"code":"VALIDATION_ERROR","errors":[{"field":"name","message":"Informe seu nome."},""" +
                    """{"field":"password","message":"A senha deve ter pelo menos 8 caracteres."}]}""",
            )
        assertEquals(
            ApiError.Validation(
                listOf(
                    FieldError("name", "Informe seu nome."),
                    FieldError("password", "A senha deve ter pelo menos 8 caracteres."),
                ),
            ),
            ProblemMapper.map(dto),
        )
    }

    @Test
    fun theServerDetailNeverReachesTheError() {
        expected.keys.forEach { code ->
            val detail =
                json
                    .decodeFromString(
                        DetailOnly.serializer(),
                        ContractFixtures.byFile("problem/$code.json"),
                    ).detail
            val mapped = ProblemMapper.map(problem(code)).toString()
            assertFalse(detail.isNotBlank() && mapped.contains(detail), "$code leaks its detail")
        }
    }

    @kotlinx.serialization.Serializable
    private data class DetailOnly(
        val detail: String = "",
    )
}
