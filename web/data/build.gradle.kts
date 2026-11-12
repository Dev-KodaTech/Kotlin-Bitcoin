// :data — the only module with Ktor; implements the ports of :state (research.md W-02, W-05).
// Exceptions stop here and become Outcome values (Principle III). No Compose.
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // Unit tests run in Node only (W-18); the browser adapters are covered by E2E.
            testTask {
                enabled = false
            }
        }
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":state"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.js)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
