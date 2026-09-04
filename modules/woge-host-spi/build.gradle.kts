import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("dev.woge.kotlin-jvm-library")
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

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.named("check") {
    dependsOn(tasks.named("checkKotlinAbi"))
}
