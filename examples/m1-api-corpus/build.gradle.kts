plugins {
    id("dev.woge.kotlin-jvm-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
}

description = "Compact compile-verified examples and negative compiler fixtures for the public M1 API."

dependencies {
    implementation(project(":woge-core"))
    implementation(project(":woge-protocol"))
    implementation(project(":woge-host-spi"))
    implementation(project(":woge-spring-webflux"))
    implementation(project(":woge-spring-mvc"))
    implementation(project(":woge-ktor"))
    ksp(project(":woge-ksp"))

    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotlinCompilerEmbeddable)
    testImplementation(libs.kotlinxSerializationJson)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    val root = rootProject.layout.projectDirectory
    systemProperty("woge.repository.root", root.asFile.absolutePath)
    // The index tests read these repository files directly; changes must rerun them.
    inputs
        .files(
            root.file("docs/ai-dx/woge-framework-index.json"),
            root.file("config/architecture/module-boundaries.tsv"),
            root.file("gradle.properties"),
            root.file("gradle/libs.versions.toml"),
            root.file("client/woge-fallback-client/package.json"),
            root.dir("scaffolds/spring-boot"),
        ).withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("repositoryIndexFiles")
}
