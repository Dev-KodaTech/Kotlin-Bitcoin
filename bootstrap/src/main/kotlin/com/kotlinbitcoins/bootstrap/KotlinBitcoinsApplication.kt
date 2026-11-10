package com.kotlinbitcoins.bootstrap

import org.springframework.amqp.rabbit.annotation.EnableRabbit
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

private const val PERSISTENCE_PACKAGE = "com.kotlinbitcoins.infrastructure.persistence"
private const val MESSAGING_PACKAGE = "com.kotlinbitcoins.infrastructure.messaging"

/**
 * Composition root. Component scanning covers only the HTTP layer and this module; adapters and use cases are
 * plain classes registered with `@Bean` in [AdapterConfiguration] and [UseCaseConfiguration].
 */
@SpringBootApplication(scanBasePackages = ["com.kotlinbitcoins.api", "com.kotlinbitcoins.bootstrap"])
@EnableJpaRepositories(basePackages = [PERSISTENCE_PACKAGE, MESSAGING_PACKAGE])
@EntityScan(basePackages = [PERSISTENCE_PACKAGE, MESSAGING_PACKAGE])
@EnableScheduling
@EnableRabbit
class KotlinBitcoinsApplication

// The spread copies the args array once, at startup.
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<KotlinBitcoinsApplication>(*args)
}
