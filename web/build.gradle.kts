import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

val catalog = libs
val javaVersion = 21

// Modules where floating point is forbidden for values (FR-038, research.md W-06).
val precisionModules = setOf("core", "state", "data")

// Type usages and literals: `: Double`, `<Float>`, `, Double`, `Double(`, `Float.MAX_VALUE`, `as Double`, `1f`, `1.5F`.
val floatingPointPatterns =
    listOf(
        """:\s*(Double|Float)\b""",
        """[<,]\s*(Double|Float)\b""",
        """\b(Double|Float)\s*\(""",
        """\b(Double|Float)\.""",
        """\bas\??\s+(Double|Float)\b""",
        """\b\d+(\.\d+)?[fF]\b""",
    )
val floatConversionPatterns = listOf("""\.to(Double|Float)(OrNull)?\s*\(""")
val consoleOutputPatterns = listOf("""\bprintln\s*\(""", """\bprint\s*\(""")

// Node, Yarn and Binaryen come from the repositories declared in settings.gradle.kts (FAIL_ON_PROJECT_REPOS):
// without a download URL the Kotlin Gradle plugin does not add its own repository to the project.
allprojects {
    plugins.withType<WasmNodeJsPlugin> { the<WasmNodeJsEnvSpec>().downloadBaseUrl.set(null as String?) }
    plugins.withType<WasmNodeJsRootPlugin> { the<WasmNodeJsEnvSpec>().downloadBaseUrl.set(null as String?) }
    plugins.withType<WasmYarnPlugin> { the<WasmYarnRootEnvSpec>().downloadBaseUrl.set(null as String?) }
    plugins.withType<BinaryenPlugin> { the<BinaryenEnvSpec>().downloadBaseUrl.set(null as String?) }
}

subprojects {
    group = "com.kotlinbitcoins.web"
    version = "0.1.0-SNAPSHOT"

    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    listOf("org.jetbrains.kotlin.multiplatform", "org.jetbrains.kotlin.jvm").forEach { kotlinPlugin ->
        pluginManager.withPlugin(kotlinPlugin) {
            configure<KotlinProjectExtension> {
                jvmToolchain(javaVersion)
            }
        }
    }

    configure<KtlintExtension> {
        version.set(catalog.versions.ktlint.asProvider())
        // Generated sources (Compose Resources' Res class, fixtures) are not ours to format.
        filter {
            exclude { element -> element.file.invariantSeparatorsPath.contains("/build/") }
        }
    }

    val detektConfigs =
        buildList {
            add("config/detekt/detekt.yml")
            when (project.name) {
                in precisionModules -> add("config/detekt/detekt-precision.yml")
                "ui" -> add("config/detekt/detekt-ui.yml")
            }
        }

    configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files(detektConfigs))
        // KMP keeps sources in src/<sourceSet>/kotlin; detekt's default only covers src/main and src/test.
        source.setFrom(files("src"))
        parallel = true
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = javaVersion.toString()
    }

    // detekt 1.23.x runs its own embedded Kotlin compiler and refuses any other Kotlin version on its classpath.
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion(catalog.versions.detekt.kotlin.get())
            }
        }
    }

    tasks.matching { it.name == "check" }.configureEach {
        dependsOn("ktlintCheck", "detekt")
    }

    // detekt's ForbiddenMethodCall needs type resolution, which detekt 1.23 does not run for wasmJs sources, so the
    // rules of detekt-precision.yml and detekt-ui.yml are also enforced as text checks over the code (W-06).
    when (project.name) {
        in precisionModules ->
            registerSourcePatternCheck(
                name = "checkNoFloatingPoint",
                rule = "Floating point is forbidden for values (FR-038, W-06); use the :core/money value objects",
                patterns = floatingPointPatterns + floatConversionPatterns + consoleOutputPatterns,
            )
        "ui" ->
            registerSourcePatternCheck(
                name = "checkFloatConversionConfined",
                rule = "A value becomes Float only in ui/chart/ChartGeometry.kt (W-06); no console output (FR-008)",
                patterns = floatConversionPatterns + consoleOutputPatterns,
                excludedFile = "ui/chart/ChartGeometry.kt",
            )
        "app" ->
            registerSourcePatternCheck(
                name = "checkNoConsoleOutput",
                rule = "No console output: it could leak the session token (FR-008)",
                patterns = consoleOutputPatterns,
            )
    }
}

/**
 * Registers a task, wired into `check`, that fails when any pattern matches the production sources (src/<*>Main).
 * Comments and string/char literals are blanked first, so only real code is searched and line numbers are kept.
 * Text search is needed because kotlin.Float is implicitly imported (ForbiddenImport cannot see it) and detekt does
 * not resolve types in wasmJs sources (ForbiddenMethodCall is inert there).
 */
fun Project.registerSourcePatternCheck(
    name: String,
    rule: String,
    patterns: List<String>,
    excludedFile: String? = null,
) {
    val sources =
        fileTree("src") {
            include("*Main/**/*.kt")
            excludedFile?.let { exclude("**/$it") }
        }
    val moduleDir = projectDir

    val check =
        tasks.register(name) {
            group = "verification"
            description = "Fails on forbidden code in the production sources: $rule."
            inputs.files(sources)
            inputs.property("patterns", patterns)
            doLast {
                val ignored =
                    Regex(
                        "\"\"\"[\\s\\S]*?\"\"\"" + // raw string
                            "|\"(?:\\\\.|[^\"\\\\\\n])*\"" + // string
                            "|'(?:\\\\.|[^'\\\\\\n])+'" + // char
                            "|/\\*[\\s\\S]*?\\*/" + // block comment
                            "|//[^\\n]*", // line comment
                    )
                val forbidden = patterns.map(::Regex)
                val violations =
                    sources.files.sorted().flatMap { file ->
                        val code = file.readText().replace(ignored) { match -> match.value.replace(Regex("[^\\n]"), " ") }
                        code.lines().withIndex().filter { (_, line) -> forbidden.any { it.containsMatchIn(line) } }.map {
                            (index, line) ->
                            "${file.relativeTo(moduleDir)}:${index + 1}: ${line.trim()}"
                        }
                    }
                if (violations.isNotEmpty()) {
                    throw GradleException("$rule:\n" + violations.joinToString("\n"))
                }
            }
        }

    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(check)
    }
}
