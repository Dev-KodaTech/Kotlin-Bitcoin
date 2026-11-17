// :state — ViewModels, immutable UI states and the ports implemented by :data (research.md W-02).
// No Compose and no Ktor. Tests run in Node.
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            // ViewModel and StateFlow are part of the public API consumed by :ui.
            api(libs.lifecycle.viewmodel)
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        wasmJsTest.dependencies {
            // Named time zones for kotlinx-datetime on wasmJs (W-07): the Mercado chart marks are in Brasília time.
            implementation(npm("@js-joda/timezone", libs.versions.jsJodaTimezone.get()))
        }
    }
}
