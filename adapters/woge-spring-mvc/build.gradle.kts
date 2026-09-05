plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Spring MVC and Servlet transport adapter for Woge."

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()
}

dependencies {
    api(platform(libs.springBootDependencies))
    api(project(":woge-core"))
    api(project(":woge-protocol"))
    api(project(":woge-host-spi"))
    api(libs.jakartaServletApi)
    api(libs.springWebmvc)

    implementation(project(":woge-server-runtime"))
    implementation(libs.kotlinxCoroutinesCore)

    testImplementation(project(":woge-adapter-tck"))
    testImplementation(libs.junitJupiter)
    testImplementation(libs.springBootStarterWeb)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.named("check") {
    dependsOn(tasks.named("checkKotlinAbi"))
}
