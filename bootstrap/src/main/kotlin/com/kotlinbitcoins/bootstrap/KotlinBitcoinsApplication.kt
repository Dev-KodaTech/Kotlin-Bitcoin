package com.kotlinbitcoins.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Composition root. Component scanning covers only the HTTP layer and this module; adapters and use cases are
 * plain classes registered with `@Bean` in [AdapterConfiguration] and [UseCaseConfiguration].
 */
@SpringBootApplication(scanBasePackages = ["com.kotlinbitcoins.api", "com.kotlinbitcoins.bootstrap"])
@EnableJpaRepositories(basePackages = ["com.kotlinbitcoins.infrastructure.persistence"])
@EntityScan(basePackages = ["com.kotlinbitcoins.infrastructure.persistence"])
class KotlinBitcoinsApplication

// The spread copies the args array once, at startup.
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<KotlinBitcoinsApplication>(*args)
}
