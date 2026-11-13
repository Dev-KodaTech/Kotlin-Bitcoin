// :app — executable edge: main(), ComposeViewport and AppContainer (manual composition of every module).
// The only module that knows the concrete implementations (research.md W-02). Covered by E2E.
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":core"))
            implementation(project(":state"))
            implementation(project(":data"))
            implementation(project(":ui"))
            // ComposeViewport, the NavController and its browser binding live in this edge module.
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.navigation.compose)
            // Named time zones for kotlinx-datetime; referenced from main.kt so it stays in the bundle (W-07).
            implementation(npm("@js-joda/timezone", libs.versions.jsJodaTimezone.get()))
        }
    }
}
