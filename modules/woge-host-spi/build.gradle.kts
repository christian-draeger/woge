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

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    systemProperty("woge.xss.corpus", rootProject.file("testing/xss-corpus/payloads.tsv").absolutePath)
}

tasks.named("check") {
    dependsOn(tasks.named("checkKotlinAbi"))
}
