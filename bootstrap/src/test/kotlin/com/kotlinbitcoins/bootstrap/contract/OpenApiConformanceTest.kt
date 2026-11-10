package com.kotlinbitcoins.bootstrap.contract

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path

/**
 * The document springdoc generates from the controllers must match the source contract
 * (specs/001-bitcoin-investment-api/contracts/openapi.yaml): same paths, methods, response statuses and `required`
 * fields of the request and response schemas (R-10). Any difference fails the build.
 */
class OpenApiConformanceTest : ApplicationTestBase() {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val contract: JsonNode by lazy {
        objectMapper.valueToTree(
            Yaml().load<Any>(Files.readString(contractFile())),
        )
    }

    private val generated: JsonNode by lazy {
        val body =
            mockMvc
                .get("/v3/api-docs")
                .andReturn()
                .response.contentAsString
        objectMapper.readTree(body)
    }

    @Test
    fun `generated document exposes the same paths and methods as the contract`() {
        assertThat(operations(generated)).containsExactlyInAnyOrderElementsOf(operations(contract))
    }

    @Test
    fun `every operation answers the same statuses as the contract`() {
        val differences =
            operations(contract).mapNotNull { operation ->
                val expected = statuses(contract, operation)
                val actual = statuses(generated, operation)
                if (expected == actual) null else "$operation: contract=$expected generated=$actual"
            }

        assertThat(differences).isEmpty()
    }

    @Test
    fun `request and response schemas have the same required fields as the contract`() {
        val differences = mutableListOf<String>()
        for (operation in operations(contract)) {
            val expectedOperation = operationNode(contract, operation)
            val actualOperation = operationNode(generated, operation)

            schemaOf(contract, expectedOperation.path("requestBody"))?.let { expected ->
                compare(
                    expected,
                    schemaOf(generated, actualOperation.path("requestBody")),
                    "$operation request",
                    differences,
                )
            }
            expectedOperation
                .path("responses")
                .properties()
                .filter { (status, _) -> status.startsWith("2") }
                .forEach { (status, response) ->
                    schemaOf(contract, response)?.let { expected ->
                        val actual = schemaOf(generated, actualOperation.path("responses").path(status))
                        compare(expected, actual, "$operation $status", differences)
                    }
                }
        }

        assertThat(differences).isEmpty()
    }

    @Test
    fun `the document declares the bearer scheme and the business server`() {
        assertThat(generated.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer")
        assertThat(generated.path("servers").map { it.path("url").asText() }).contains("/api/v1")
        assertThat(generated.at("/info/title").asText()).isNotBlank()
        assertThat(generated.at("/info/version").asText()).isNotBlank()
    }

    private fun compare(
        expected: JsonNode,
        actual: JsonNode?,
        where: String,
        differences: MutableList<String>,
    ) {
        if (actual == null) {
            differences += "$where: schema missing in the generated document"
            return
        }
        val expectedRequired = expected.path("required").map { it.asText() }.toSet()
        val actualRequired = actual.path("required").map { it.asText() }.toSet()
        if (expectedRequired != actualRequired) {
            differences += "$where: required contract=$expectedRequired generated=$actualRequired"
        }
        for ((name, property) in expected.path("properties").properties()) {
            val actualProperty = actual.path("properties").get(name)
            if (actualProperty == null) {
                differences += "$where: property '$name' missing in the generated document"
                continue
            }
            val expectedProperty = resolve(contract, property)
            val resolvedActual = resolve(generated, actualProperty)
            if (expectedProperty.has(
                    "properties",
                )
            ) {
                compare(expectedProperty, resolvedActual, "$where.$name", differences)
            }
            val expectedItems = expectedProperty.path("items")
            if (!expectedItems.isMissingNode) {
                compare(
                    resolve(contract, expectedItems),
                    resolve(generated, resolvedActual.path("items")),
                    "$where.$name[]",
                    differences,
                )
            }
        }
        val expectedItems = expected.path("items")
        if (!expectedItems.isMissingNode) {
            compare(resolve(contract, expectedItems), resolve(generated, actual.path("items")), "$where[]", differences)
        }
    }

    /** The JSON schema of the `application/json` body of a request or response node, with `$ref`s resolved. */
    private fun schemaOf(
        document: JsonNode,
        node: JsonNode,
    ): JsonNode? {
        val body = resolve(document, node)
        val schema = body.path("content").path("application/json").path("schema")
        return if (schema.isMissingNode) null else resolve(document, schema)
    }

    private fun resolve(
        document: JsonNode,
        node: JsonNode,
    ): JsonNode {
        var current = node
        while (true) {
            current =
                when {
                    current.has("\$ref") -> document.at(current.path("\$ref").asText().removePrefix("#"))
                    else -> nonNullBranch(current) ?: return current
                }
        }
    }

    /** `oneOf`/`anyOf: [X, {type: 'null'}]` (a nullable property) is X; anything else is not a wrapper. */
    private fun nonNullBranch(node: JsonNode): JsonNode? {
        val branches =
            (node.path("oneOf").takeIf { it.isArray } ?: node.path("anyOf").takeIf { it.isArray }) ?: return null
        val nonNull = branches.filterNot { it.path("type").asText() == "null" }
        return if (nonNull.size == 1 && nonNull.size < branches.size()) nonNull.single() else null
    }

    private fun operations(document: JsonNode): Set<String> =
        document
            .path("paths")
            .properties()
            .flatMap { (path, item) ->
                item.properties().filter { (method, _) -> method in HTTP_METHODS }.map { (method, _) ->
                    "${method.uppercase()} ${normalized(path)}"
                }
            }.toSet()

    private fun operationNode(
        document: JsonNode,
        operation: String,
    ): JsonNode {
        val (method, path) = operation.split(" ", limit = 2)
        val found =
            document
                .path("paths")
                .properties()
                .firstOrNull { (candidate, _) -> normalized(candidate) == path }
                ?.value
                ?.path(method.lowercase())
        return found ?: objectMapper.createObjectNode()
    }

    private fun statuses(
        document: JsonNode,
        operation: String,
    ): Set<String> =
        operationNode(document, operation)
            .path("responses")
            .properties()
            .map { it.key }
            .toSet()

    private fun normalized(path: String): String = path.removePrefix("/api/v1")

    private fun contractFile(): Path {
        var directory: Path? = Path.of("").toAbsolutePath()
        while (directory != null) {
            val candidate = directory.resolve("specs/001-bitcoin-investment-api/contracts/openapi.yaml")
            if (Files.exists(candidate)) return candidate
            directory = directory.parent
        }
        error("contracts/openapi.yaml not found above ${Path.of("").toAbsolutePath()}")
    }

    private companion object {
        val HTTP_METHODS = setOf("get", "post", "put", "patch", "delete")
    }
}
