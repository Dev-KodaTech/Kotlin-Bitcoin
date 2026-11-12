// :spike-a11y — throwaway accessibility go/no-go spike (research.md W-00, tasks T014–T023).
// Same stack and versions as the real client (W-01). Removed after "W-00 Resultado" is recorded.
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "spike.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.core)
            // Compatibility probes (T014): exact decimal (W-06) and named time zones (W-07).
            implementation(libs.bignum)
            implementation(libs.kotlinx.datetime)
            implementation(npm("@js-joda/timezone", libs.versions.jsJodaTimezone.get()))
        }
    }
}
