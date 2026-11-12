// :ui — stateless Compose screens, components, theme, navigation and pt-BR strings (research.md W-02).
// MUST NOT depend on :data or Ktor: a screen cannot call the API (Principle I).
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
            // Tests read Compose Resources, so they run in headless Chrome (W-18).
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        // Compose UI tests need a webpack bundle to load the Skiko runtime (CMP-4906).
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":state"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.material3)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.resources {
    packageOfResClass = "com.kotlinbitcoins.web.ui.resources"
}
