plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

// Composition root: the only module that knows every other one.
dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure"))
    implementation(project(":api"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.actuator)
    // Only for @EnableRabbit, RabbitTemplate and JavaMailSender when wiring the messaging and e-mail adapters.
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.spring.boot.starter.mail)
    // Only for @EnableJpaRepositories/@EntityScan and PlatformTransactionManager when wiring the adapters.
    implementation(libs.spring.boot.starter.data.jpa)
    // Only for StringRedisTemplate when wiring the quote cache.
    implementation(libs.spring.boot.starter.data.redis)
    // Only for @EnableSchedulerLock and the JDBC LockProvider when wiring the scheduled jobs.
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.provider.jdbc.template)
    implementation(libs.logstash.logback.encoder)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.wiremock)
    testImplementation(libs.springmockk)
}

// Only the executable jar is produced here; the other modules build plain jars.
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("kotlin-bitcoins.jar")
}
