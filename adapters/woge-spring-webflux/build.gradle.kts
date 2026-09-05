import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Spring WebFlux transport adapter for Woge."

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()
}

dependencies {
    api(platform(libs.springBootDependencies))
    api(project(":woge-core"))
    api(project(":woge-protocol"))
    api(project(":woge-host-spi"))
    api(libs.springWebflux)

    implementation(project(":woge-server-runtime"))
    implementation(libs.kotlinxCoroutinesReactor)

    testImplementation(libs.junitJupiter)
    testImplementation(libs.reactorNettyHttp)
    testImplementation(libs.springContext)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.named("check") {
    dependsOn(tasks.named("checkKotlinAbi"))
}
