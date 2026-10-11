import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import java.io.File

plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Ktor transport adapter for Woge."

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()
}

dependencies {
    api(project(":woge-core"))
    api(project(":woge-protocol"))
    api(project(":woge-host-spi"))
    api(libs.ktorServerCore)

    implementation(project(":woge-server-runtime"))

    testImplementation(project(":woge-adapter-tck"))
    testImplementation(libs.junitJupiter)
    testImplementation(libs.ktorServerNetty)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.named("check") {
    dependsOn(tasks.named("checkKotlinAbi"))
}

tasks.test {
    inputs.property("streamProxyProfile", providers.environmentVariable("WOGE_STREAM_PROXY_PROFILE").orElse("nginx"))
    inputs.property("streamProxyScript", providers.environmentVariable("WOGE_STREAM_PROXY_SCRIPT").orElse(""))
    inputs.files(
        providers
            .environmentVariable("WOGE_STREAM_PROXY_SCRIPT")
            .map {
                listOf(
                    it,
                    File(it).parentFile.resolve("nginx.conf").absolutePath,
                    File(it).parentFile.resolve("Caddyfile").absolutePath,
                )
            }.orElse(emptyList()),
    )
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
