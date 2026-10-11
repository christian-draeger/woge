import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Host-neutral document, patch, and live-frame protocol values."

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()
}

dependencies {
    api(project(":woge-core"))

    implementation(libs.jsoup)
    implementation(libs.kotlinxSerializationJson)

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    systemProperty("woge.xss.corpus", rootProject.file("testing/xss-corpus/payloads.tsv").absolutePath)
    systemProperty(
        "woge.fuzz.iterations",
        providers
            .gradleProperty("woge.fuzz.iterations")
            .orElse(providers.environmentVariable("WOGE_FUZZ_ITERATIONS"))
            .orElse("128"),
    )
}

tasks.named("check") {
    dependsOn(tasks.named("checkKotlinAbi"))
}
