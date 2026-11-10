plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":application"))

    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.spring.boot.starter.mail)
    // NimbusJwtEncoder for issuing tokens
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.security.crypto)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.provider.jdbc.template)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.wiremock)
    testImplementation(libs.mockk)
}
