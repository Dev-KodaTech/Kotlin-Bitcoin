// Pure Kotlin: depends only on domain (R-02). Domain value objects are part of the use case contract.
dependencies {
    api(project(":domain"))

    testImplementation(libs.bundles.unit.test)
    testImplementation(libs.mockk)
}
