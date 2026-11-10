package com.kotlinbitcoins.bootstrap

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
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
            ).servers(listOf(Server().url("/api/v1")))
            .components(
                Components().addSecuritySchemes(
                    BEARER_AUTH,
                    SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
                ),
            ).addSecurityItem(SecurityRequirement().addList(BEARER_AUTH))

    private companion object {
        const val BEARER_AUTH = "bearerAuth"
    }
}
