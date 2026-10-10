import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Development-only SSE browser channel and optional accessible status overlay."

tasks.named<ProcessResources>("processResources") {
    from(rootProject.layout.projectDirectory.file("client/woge-fallback-client/src/state-controls.js")) {
        into("dev/woge/development/browser")
    }
}

dependencies {
    api(project(":woge-dev-orchestrator"))
    api(project(":woge-core"))
    api(project(":woge-dev-client"))
    implementation(libs.kotlinxSerializationJson)

    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotlinxCoroutinesTest)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

val productionArtifacts =
    rootProject
        .file("config/architecture/module-boundaries.tsv")
        .readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split('\t') }
        .filter { it[1] !in setOf("tooling", "tooling-model", "test-support") }

tasks.test {
    dependsOn(productionArtifacts.map { ":${it[0]}:jar" })
    val artifacts =
        files(
            productionArtifacts.map {
                rootProject.layout.projectDirectory.file("${it[3]}/build/libs/${it[0]}-$version.jar")
            },
        )
    inputs.files(artifacts).withPropertyName("productionArtifacts")
    systemProperty("woge.production.artifacts", artifacts.asPath)
}

tasks.register<JavaExec>("runBrowserFixture") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.woge.development.browser.BrowserFixture"
}
