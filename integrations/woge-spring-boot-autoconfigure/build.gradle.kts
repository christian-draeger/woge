import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("dev.woge.kotlin-jvm-library")
    id("org.jetbrains.kotlin.kapt")
}

description = "Conditional Spring Boot wiring and diagnostics for the Woge server adapters."

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()
}

dependencies {
    api(platform(libs.springBootDependencies))
    api(project(":woge-host-spi"))
    api(libs.springBootAutoconfigure)

    compileOnly(project(":woge-spring-mvc"))
    compileOnly(project(":woge-spring-webflux"))

    kapt(libs.springBootConfigurationProcessor)

    testImplementation(project(":woge-spring-webflux"))
    testImplementation(project(":woge-spring-mvc"))
    testImplementation(libs.assertjCore)
    testImplementation(libs.jakartaServletApi)
    testImplementation(libs.junitJupiter)
    testImplementation(libs.springBoot)
    testImplementation(libs.springBootTest)
    testImplementation(libs.springWebmvc)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.named("check") {
    dependsOn(tasks.named("checkKotlinAbi"))
}

tasks.named<Jar>("jar") {
    manifest.attributes["Implementation-Version"] = project.version
}
