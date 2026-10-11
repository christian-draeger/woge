import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("dev.woge.kotlin-jvm-library")
    alias(libs.plugins.kotlinSerialization)
}

description = "Framework-neutral page, action, and live-update host ports."

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()
}

dependencies {
    api(project(":woge-core"))
    api(project(":woge-protocol"))
    api(libs.kotlinxCoroutinesCore)
    api(libs.kotlinxSerializationJson)

    testImplementation(libs.kotlinxCoroutinesTest)
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
