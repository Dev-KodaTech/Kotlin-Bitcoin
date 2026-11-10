package com.kotlinbitcoins.api.support

import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.format.ApiFormats
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import java.time.ZoneId

/**
 * Shared setup for `@WebMvcTest` slices of controllers: Problem Details mapper, API formats in the business zone
 * and a permissive security chain (authentication itself is covered by the contract tests in `bootstrap`).
 */
@TestConfiguration
@Import(ProblemDetailsMapper::class, ApiFormats::class)
class WebMvcTestConfiguration {
    @Bean
    fun businessZone(): ZoneId = ZoneId.of("America/Sao_Paulo")

    @Bean
    fun permitAll(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
}
