import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import java.io.File

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

    testImplementation(project(":woge-adapter-tck"))
    testImplementation(libs.junitJupiter)
    testImplementation(libs.reactorNettyHttp)
    testImplementation(libs.springContext)
    testImplementation(libs.springSecurityConfig)
    testImplementation(libs.springSecurityWeb)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.named("check") {
    dependsOn(tasks.named("checkKotlinAbi"))
}

tasks.test {
    inputs.property("nativeFormBrowserScript", providers.environmentVariable("WOGE_NATIVE_BROWSER_SCRIPT").orElse(""))
    inputs.files(
        providers
            .environmentVariable("WOGE_NATIVE_BROWSER_SCRIPT")
            .map {
                listOf(
                    it,
                    File(it)
                        .parentFile.parentFile
                        .resolve("dist/woge-fallback.js")
                        .absolutePath,
                )
            }.orElse(emptyList()),
    )
}
