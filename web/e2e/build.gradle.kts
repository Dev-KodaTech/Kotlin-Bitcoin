// :e2e — Playwright for Java + axe-core against the running stack (research.md W-17).
// Runs only through `./gradlew :e2e:e2eTest`; it is not part of `check`.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.playwright)
    testImplementation(libs.axe.core.playwright)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    enabled = false
}

tasks.register<Test>("e2eTest") {
    description = "Runs the end-to-end tests against a running stack (-PbaseUrl, -PapiUrl, -Pbrowsers)."
    group = "verification"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    systemProperty("e2e.baseUrl", findProperty("baseUrl") ?: "http://localhost:8081")
    systemProperty("e2e.apiUrl", findProperty("apiUrl") ?: "http://localhost:8081/api/v1")
    systemProperty("e2e.browsers", findProperty("browsers") ?: "chromium,firefox,webkit")
    // Screenshot references (W-13 chart): -PupdateSnapshots=true writes them instead of comparing.
    systemProperty("e2e.updateSnapshots", findProperty("updateSnapshots") ?: "false")
    val snapshotsDir = layout.projectDirectory.dir("src/test/resources/snapshots")
    systemProperty("e2e.snapshotsDir", snapshotsDir.asFile.path)
    // The result depends on the external stack, never on the inputs alone.
    outputs.upToDateWhen { false }
}

// Classpath of the tests, for `java -cp … com.microsoft.playwright.CLI install --with-deps` in CI (T223).
tasks.register("printTestClasspath") {
    description = "Prints the runtime classpath of the E2E tests."
    group = "help"
    val classpath = sourceSets.test.get().runtimeClasspath
    doLast { println(classpath.asPath) }
}
