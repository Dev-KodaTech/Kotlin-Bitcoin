package com.kotlinbitcoins.bootstrap

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Metadata of the document served at `/v3/api-docs` and `/swagger-ui.html` (T228). */
@Configuration
class OpenApiConfiguration {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("KotlinBitcoins — API de Investimento em Bitcoins")
                    .version("1.0.0")
                    .description(
                        "Cadastro, depósito em reais, compra e resgate de bitcoin, extrato e informações de mercado.",
                    ),
            ).servers(listOf(Server().url(BASE_PATH)))
            .components(
                Components().addSecuritySchemes(
                    BEARER_AUTH,
                    SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
                ),
            ).addSecurityItem(SecurityRequirement().addList(BEARER_AUTH))

    /**
     * Controllers are mapped under `/api/v1`, which is also the server URL (as in contracts/openapi.yaml).
     * Paths are made relative to it, otherwise Swagger UI calls `/api/v1/api/v1/...`.
     */
    @Bean
    fun relativePathsCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            val original = openApi.paths ?: return@OpenApiCustomizer
            val relative = Paths()
            original.forEach { (path, item) -> relative.addPathItem(path.removePrefix(BASE_PATH), item) }
            openApi.paths = relative
        }

    private companion object {
        const val BEARER_AUTH = "bearerAuth"
        const val BASE_PATH = "/api/v1"
    }
}
