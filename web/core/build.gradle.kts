// :core — pure module (research.md W-02): value objects, pt-BR parsing/formatting, routes and the error model.
// No Compose, no Ktor, no browser API. Tests run in Node.
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            // Encapsulated in :core/money; never part of the public API (W-06).
            implementation(libs.bignum)
            api(libs.kotlinx.datetime)
            // Only the @Serializable annotations of the type-safe routes (AppRoute).
            api(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        wasmJsTest.dependencies {
            // Named time zones for kotlinx-datetime on wasmJs (W-07); BrasiliaTime tests need America/Sao_Paulo.
            implementation(npm("@js-joda/timezone", libs.versions.jsJodaTimezone.get()))
        }
    }
}
