package com.kotlinbitcoins.bootstrap

import org.springframework.context.annotation.Configuration

/**
 * Registers each use case as a `@Bean`, built from its ports. Use cases are plain Kotlin classes from the
 * `application` module and are never component-scanned. Each user story adds its use cases here.
 */
@Configuration
class UseCaseConfiguration
