import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

val catalog = libs
val javaVersion = 21

subprojects {
    group = "com.kotlinbitcoins"
    version = "0.1.0-SNAPSHOT"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<KotlinJvmProjectExtension> {
        jvmToolchain(javaVersion)
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    configure<KtlintExtension> {
        version.set(catalog.versions.ktlint.asProvider())
    }

    configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        parallel = true
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = javaVersion.toString()
    }

    // Spring modules (infrastructure, api, bootstrap) import the Spring Boot BOM. Kotlin, JUnit, AssertJ and
    // Testcontainers stay on the catalog versions so every module uses the same ones.
    pluginManager.withPlugin("io.spring.dependency-management") {
        extra["kotlin.version"] = catalog.versions.kotlin.get()
        extra["junit-jupiter.version"] = catalog.versions.junit.get()
        extra["assertj.version"] = catalog.versions.assertj.get()
        extra["testcontainers.version"] = catalog.versions.testcontainers.get()
        configure<DependencyManagementExtension> {
            imports {
                mavenBom(SpringBootPlugin.BOM_COORDINATES)
            }
        }
        // detekt 1.23.x runs its own embedded Kotlin compiler and refuses any other Kotlin version on its classpath.
        // Registered here, after the plugin's own rule, so this pin wins over the BOM's kotlin.version.
        configurations.matching { it.name == "detekt" }.configureEach {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.jetbrains.kotlin") {
                    useVersion(catalog.versions.detekt.kotlin.get())
                }
            }
        }
    }

    dependencies {
        "testImplementation"(platform(catalog.junit.bom))
        "testRuntimeOnly"(catalog.junit.platform.launcher)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.named("check") {
        dependsOn("ktlintCheck", "detekt")
    }
}
