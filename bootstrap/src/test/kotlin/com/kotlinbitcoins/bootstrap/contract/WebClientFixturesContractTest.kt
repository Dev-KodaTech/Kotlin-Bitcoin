package com.kotlinbitcoins.bootstrap.contract

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.Request
import com.atlassian.oai.validator.model.SimpleResponse
import com.atlassian.oai.validator.report.ValidationReport
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path

/**
 * The JSON fixtures the web client tests with (`web/contract-fixtures`, listed in its `manifest.json`) must follow the
 * source contract (specs/001-bitcoin-investment-api/contracts/openapi.yaml) and have the shape of the responses the
 * assembled application really gives (feature 002, research W-12, contracts/fixtures-manifest.md, checks 1 to 4).
 * Each fixture is a dynamic test. Entries with `pendingAmendment` are skipped until the contract has the operation.
 */
class WebClientFixturesContractTest : ApplicationTestBase() {
    private val mapper = ObjectMapper()
    private val fixtures: ClientFixtures by lazy { ClientFixtures.load(mapper) }
    private val contract: OpenApiContract by lazy { OpenApiContract(fixtures.contractFile, mapper) }

    /** Scenarios named by `realScenario` in the manifest; each one starts from the clean state of the base class. */
    private val realScenarios: Map<String, () -> ResultActionsDsl> =
        mapOf(
            "registerAccount" to { postJson("/api/v1/accounts", account(email = ANA_EMAIL)) },
            "loginOk" to {
                api.register("Ana", ANA_EMAIL)
                api.login(ANA_EMAIL)
            },
            "registerInvalidEmail" to { postJson("/api/v1/accounts", account(email = "ana-sem-arroba")) },
            "registerDuplicateEmail" to {
                api.register("Ana", ANA_EMAIL)
                postJson("/api/v1/accounts", account(email = ANA_EMAIL))
            },
            "loginBlankEmail" to { api.login(email = "") },
            "loginWrongPassword" to {
                api.register("Ana", ANA_EMAIL)
                api.login(ANA_EMAIL, "senha-errada-123")
            },
            "balanceWithoutToken" to { mockMvc.get("/api/v1/me/balance") },
            "depositWithoutToken" to { postJson("/api/v1/me/deposits", """{"amount":"10.00"}""") },
            "depositZero" to { api.deposit(api.register("Ana", ANA_EMAIL), "0") },
            "quoteSourceDown" to {
                val ana = api.register("Ana", ANA_EMAIL)
                quoteSourceDown()
                api.get("/api/v1/quotes/current", ana)
            },
            "purchaseBelowOneSatoshi" to {
                stubQuote(buy = "2000000.00", sell = "2000000.00")
                val ana = api.register("Ana", ANA_EMAIL)
                api.depositOk(ana, "1.00")
                api.purchase(ana, "0.01")
            },
            "purchaseAboveBalance" to {
                stubQuote(buy = "400000.00", sell = "400000.00")
                val ana = api.register("Ana", ANA_EMAIL)
                api.depositOk(ana, "100.00")
                api.purchase(ana, "100.01")
            },
            "redemptionAboveAvailable" to {
                stubQuote(buy = "400000.00", sell = "400000.00")
                val ana = api.register("Ana", ANA_EMAIL)
                api.depositOk(ana, "100.00")
                api.purchaseOk(ana, "100.00")
                api.redeem(ana, "100.01")
            },
            "statementInvalidRange" to {
                api.get("/api/v1/me/statement?from=2026-11-10&to=2026-11-01", api.register("Ana", ANA_EMAIL))
            },
        )

    @Test
    fun `the manifest has unique scenarios and every file exists`() {
        assertThat(fixtures.entries).isNotEmpty()
        assertThat(fixtures.entries.map { it.scenario }).doesNotHaveDuplicates()
        assertThat(fixtures.entries).allSatisfy { assertThat(fixtures.exists(it.file)).describedAs(it.file).isTrue() }
    }

    @TestFactory
    fun `check 1 - every fixture is accepted by the contract schema of its operation and status`(): List<DynamicTest> =
        fixtures.entries.map { entry ->
            dynamicTest(entry.scenario) {
                skipIfPending(entry)
                val body = fixtures.json(entry.file)

                assertThat(contract.validateResponse(entry, body)).isEmpty()
                if (entry.isProblem) assertThat(body.path("status").asInt()).isEqualTo(entry.status)
            }
        } +
            fixtures.problemFiles.map { file ->
                dynamicTest("$file is a Problem") {
                    assertThat(contract.validateProblem(fixtures.json(file))).isEmpty()
                }
            }

    @Test
    fun `check 2 - the codes of the Problem fixtures are exactly the Problem code enum of the contract`() {
        val codeByFile = fixtures.problemFiles.associateWith { fixtures.json(it).path("code").asText() }

        assertThat(codeByFile).allSatisfy { file, code -> assertThat(file).isEqualTo("problem/$code.json") }
        assertThat(codeByFile.values).containsExactlyInAnyOrderElementsOf(contract.problemCodes())
    }

    @TestFactory
    fun `check 3 - every manifest entry is an operation of the contract with that status`(): List<DynamicTest> =
        fixtures.entries.map { entry ->
            dynamicTest(entry.scenario) {
                skipIfPending(entry)

                assertThat(contract.statuses(entry.method, entry.path))
                    .describedAs("statuses documented for ${entry.method} ${entry.path}")
                    .contains(entry.status)
            }
        }

    @TestFactory
    fun `check 4 - the real response of each realScenario has the shape of its fixture`(): List<DynamicTest> =
        fixtures.entries.filter { it.realScenario != null }.map { entry ->
            dynamicTest("${entry.scenario} <- ${entry.realScenario}") {
                skipIfPending(entry)
                val scenario =
                    requireNotNull(realScenarios[entry.realScenario]) {
                        "realScenario '${entry.realScenario}' has no lambda in WebClientFixturesContractTest"
                    }
                // @BeforeEach runs once per @TestFactory, not once per dynamic test.
                resetState()

                val response = scenario().andReturn().response
                val real = mapper.readTree(response.getContentAsString(Charsets.UTF_8))

                assertThat(response.status).isEqualTo(entry.status)
                assertThat(contract.shapeDifferences(entry, fixtures.json(entry.file), real)).isEmpty()
            }
        }

    private fun skipIfPending(entry: FixtureEntry) {
        entry.pendingAmendment?.let {
            Assumptions.abort<Unit>("${entry.file}: waiting for the contract amendment '$it'")
        }
    }

    private fun postJson(
        path: String,
        body: String,
    ): ResultActionsDsl =
        mockMvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun account(email: String): String = """{"name":"Ana Souza","email":"$email","password":"s3nha-forte"}"""

    private companion object {
        const val ANA_EMAIL = "ana@example.com"
    }
}

/** One entry of `web/contract-fixtures/manifest.json` (contracts/fixtures-manifest.md of feature 002). */
private data class FixtureEntry(
    val file: String,
    val method: String,
    val path: String,
    val status: Int,
    val scenario: String,
    val realScenario: String?,
    val pendingAmendment: String?,
) {
    val isProblem: Boolean get() = file.startsWith("problem/")
}

/** The web client fixtures, found by walking up from the working directory like the contract tests find `specs/`. */
private class ClientFixtures(
    private val root: Path,
    val contractFile: Path,
    val entries: List<FixtureEntry>,
    private val mapper: ObjectMapper,
) {
    val problemFiles: List<String> =
        Files.list(root.resolve("problem")).use { files ->
            files
                .toList()
                .map { "problem/${it.fileName}" }
                .filter { it.endsWith(".json") }
                .sorted()
        }

    fun exists(file: String): Boolean = Files.isRegularFile(root.resolve(file))

    fun json(file: String): JsonNode = mapper.readTree(root.resolve(file).toFile())

    companion object {
        private const val MANIFEST = "web/contract-fixtures/manifest.json"

        fun load(mapper: ObjectMapper): ClientFixtures {
            val manifestFile = locate(MANIFEST)
            val repositoryRoot = manifestFile.parent.parent.parent
            val manifest = mapper.readTree(manifestFile.toFile())
            return ClientFixtures(
                root = manifestFile.parent,
                contractFile = repositoryRoot.resolve(manifest.text("contract")),
                entries = manifest.path("fixtures").map { it.toEntry() },
                mapper = mapper,
            )
        }

        private fun JsonNode.toEntry(): FixtureEntry =
            FixtureEntry(
                file = text("file"),
                method = text("method"),
                path = text("path"),
                status = path("status").takeIf { it.isInt }?.asInt() ?: error("manifest entry without 'status': $this"),
                scenario = text("scenario"),
                realScenario = path("realScenario").textValue(),
                pendingAmendment = path("pendingAmendment").textValue(),
            )

        private fun JsonNode.text(field: String): String =
            path(field).textValue() ?: error("manifest entry without '$field': $this")

        private fun locate(relative: String): Path {
            var directory: Path? = Path.of("").toAbsolutePath()
            while (directory != null) {
                val candidate = directory.resolve(relative)
                if (Files.exists(candidate)) return candidate
                directory = directory.parent
            }
            error("$relative not found above ${Path.of("").toAbsolutePath()}")
        }
    }
}

/** The source contract: statuses per operation, response validation and the schemas used by the shape comparison. */
private class OpenApiContract(
    file: Path,
    private val mapper: ObjectMapper,
) {
    private val text = Files.readString(file)
    private val document: JsonNode = mapper.valueToTree(Yaml().load<Any>(text))
    private val serverUrl = document.at("/servers/0/url").asText()
    private val validator = OpenApiInteractionValidator.createForInlineApiSpecification(text).build()

    /** The contract plus one probe operation whose only response is `Problem`, for fixtures of no documented status. */
    private val problemValidator =
        OpenApiInteractionValidator
            .createForInlineApiSpecification(mapper.writeValueAsString(withProblemProbe()))
            .build()

    fun statuses(
        method: String,
        path: String,
    ): Set<Int> =
        operation(method, path)
            .path("responses")
            .properties()
            .mapNotNull { it.key.toIntOrNull() }
            .toSet()

    fun problemCodes(): Set<String> =
        document.at("/components/schemas/Problem/properties/code/enum").map { it.asText() }.toSet()

    fun validateResponse(
        entry: FixtureEntry,
        body: JsonNode,
    ): List<String> {
        val mediaType = mediaType(entry) ?: return listOf("${entry.method} ${entry.path} ${entry.status} has no body")
        return validator.errors(entry.method, serverUrl + entry.path, response(entry.status, mediaType, body))
    }

    fun validateProblem(body: JsonNode): List<String> =
        problemValidator.errors(
            "GET",
            serverUrl + PROBE_PATH,
            response(body.path("status").asInt(), PROBLEM_JSON, body),
        )

    fun shapeDifferences(
        entry: FixtureEntry,
        fixture: JsonNode,
        real: JsonNode,
    ): List<String> = JsonShape(::resolve).differences(fixture, real, responseSchema(entry))

    fun resolve(node: JsonNode): JsonNode =
        node.path("\$ref").textValue()?.let { resolve(document.at(it.removePrefix("#"))) } ?: node

    private fun operation(
        method: String,
        path: String,
    ): JsonNode = document.path("paths").path(path).path(method.lowercase())

    private fun response(entry: FixtureEntry): JsonNode =
        resolve(operation(entry.method, entry.path).path("responses").path(entry.status.toString()))

    private fun mediaType(entry: FixtureEntry): String? =
        response(entry)
            .path("content")
            .fieldNames()
            .asSequence()
            .firstOrNull()

    private fun responseSchema(entry: FixtureEntry): JsonNode? =
        mediaType(entry)?.let { response(entry).path("content").path(it).path("schema") }

    private fun response(
        status: Int,
        mediaType: String,
        body: JsonNode,
    ): SimpleResponse =
        SimpleResponse.Builder
            .status(status)
            .withContentType(mediaType)
            .withBody(mapper.writeValueAsString(body))
            .build()

    private fun OpenApiInteractionValidator.errors(
        method: String,
        path: String,
        response: SimpleResponse,
    ): List<String> =
        validateResponse(path, Request.Method.valueOf(method), response)
            .messages
            .filter { it.level == ValidationReport.Level.ERROR }
            .map { "${it.key}: ${it.message}" }

    private fun withProblemProbe(): JsonNode =
        document.deepCopy<JsonNode>().also { copy ->
            val probe =
                mapper.readTree(
                    """
                    {"get": {"responses": {"default": {"description": "Problem", "content": {
                      "$PROBLEM_JSON": {"schema": {"${'$'}ref": "#/components/schemas/Problem"}}}}}}}
                    """.trimIndent(),
                )
            (copy.path("paths") as ObjectNode).set<JsonNode>(PROBE_PATH, probe)
        }

    private companion object {
        const val PROBLEM_JSON = "application/problem+json"
        const val PROBE_PATH = "/__web-client-fixtures__/problem"
    }
}

/**
 * Compares the shape of a fixture with a real response (check 4): the same keys at every level, the same JSON types
 * (array elements compared as the set of their shapes; an empty array matches any) and, for each string value of
 * both documents, the `pattern` (or `uuid`/`date`/`date-time` format) its contract schema declares.
 */
private class JsonShape(
    private val resolve: (JsonNode) -> JsonNode,
) {
    fun differences(
        fixture: JsonNode,
        real: JsonNode,
        schema: JsonNode?,
    ): List<String> =
        mutableListOf<String>().apply {
            compare("$", fixture, real, this)
            patternViolations("fixture $", fixture, schema, this)
            patternViolations("real $", real, schema, this)
        }

    private fun compare(
        at: String,
        fixture: JsonNode,
        real: JsonNode,
        out: MutableList<String>,
    ) {
        if (fixture.nodeType != real.nodeType) {
            out += "$at: fixture is ${fixture.nodeType}, real is ${real.nodeType}"
        } else if (fixture.isObject) {
            val fixtureKeys = fixture.fieldNames().asSequence().toSet()
            val realKeys = real.fieldNames().asSequence().toSet()
            if (fixtureKeys != realKeys) {
                out += "$at: keys only in fixture ${fixtureKeys - realKeys}, only in real ${realKeys - fixtureKeys}"
            }
            (fixtureKeys intersect realKeys).forEach { compare("$at.$it", fixture[it], real[it], out) }
        } else if (fixture.isArray && fixture.size() > 0 && real.size() > 0) {
            val fixtureShapes = fixture.map(::signature).toSet()
            val realShapes = real.map(::signature).toSet()
            if (fixtureShapes != realShapes) out += "$at[]: fixture elements $fixtureShapes, real elements $realShapes"
        }
    }

    private fun signature(node: JsonNode): String =
        when {
            node.isObject -> {
                node.properties().sortedBy { it.key }.joinToString(",", "{", "}") { "${it.key}:${signature(it.value)}" }
            }

            node.isArray -> {
                node.map(::signature).toSortedSet().joinToString("|", "[", "]")
            }

            else -> {
                node.nodeType.name
            }
        }

    private fun patternViolations(
        at: String,
        node: JsonNode,
        schema: JsonNode?,
        out: MutableList<String>,
    ) {
        if (schema == null) return
        when {
            node.isTextual -> {
                rules(schema)
                    .filterNot { it.matches(node.asText()) }
                    .forEach { out += "$at: '${node.asText()}' does not match $it" }
            }

            node.isObject -> {
                node.properties().forEach { (key, value) ->
                    patternViolations("$at.$key", value, property(schema, key), out)
                }
            }

            node.isArray -> {
                node.forEachIndexed { index, value -> patternViolations("$at[$index]", value, items(schema), out) }
            }
        }
    }

    /** The schema, the members of its `allOf` and the single non-null branch of a nullable `oneOf`/`anyOf`. */
    private fun candidates(schema: JsonNode): List<JsonNode> {
        val resolved = resolve(schema)
        val branches =
            (resolved.path("oneOf") + resolved.path("anyOf")).filterNot { resolve(it).path("type").asText() == "null" }
        val members = resolved.path("allOf").toList() + branches.takeIf { it.size == 1 }.orEmpty()
        return listOf(resolved) + members.flatMap(::candidates)
    }

    private fun rules(schema: JsonNode): List<Regex> =
        candidates(schema).flatMap { candidate ->
            listOfNotNull(
                candidate.path("pattern").textValue()?.let(::Regex),
                FORMATS[candidate.path("format").asText()],
            )
        }

    private fun property(
        schema: JsonNode,
        name: String,
    ): JsonNode? = candidates(schema).map { it.path("properties").path(name) }.firstOrNull { !it.isMissingNode }

    private fun items(schema: JsonNode): JsonNode? =
        candidates(schema).map { it.path("items") }.firstOrNull { !it.isMissingNode }

    private companion object {
        val FORMATS: Map<String, Regex> =
            mapOf(
                "uuid" to Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"),
                "date" to Regex("^\\d{4}-\\d{2}-\\d{2}$"),
                "date-time" to Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})$"),
            )
    }
}
