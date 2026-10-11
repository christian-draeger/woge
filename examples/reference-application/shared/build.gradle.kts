plugins {
    id("dev.woge.kotlin-jvm-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
}

description = "Host-neutral project page used by the executable Woge reference application."

dependencies {
    implementation(project(":woge-reference-shared-rules"))
    api(project(":woge-host-spi"))
    implementation(project(":woge-ui-headless"))
    implementation(libs.kotlinxSerializationJson)
    runtimeOnly(project(":woge-fallback-client-assets"))
    ksp(project(":woge-ksp"))

    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotlinxCoroutinesTest)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

// Example-owned deployment only; framework asset registration remains #242.
val noteRulesAssets =
    tasks.register<Sync>("noteRulesAssets") {
        dependsOn(":woge-reference-shared-rules:jsProductionExecutableCompileSync")
        from(
            project(
                ":woge-reference-shared-rules",
            ).layout.buildDirectory.dir("compileSync/js/main/productionExecutable/kotlin"),
        ) {
            include("*.mjs", "*.js")
        }
        into(layout.buildDirectory.dir("generated/note-assets/static/assets/note-rules"))
    }
sourceSets.main { resources.srcDir(layout.buildDirectory.dir("generated/note-assets")) }
tasks.processResources { dependsOn(noteRulesAssets) }
