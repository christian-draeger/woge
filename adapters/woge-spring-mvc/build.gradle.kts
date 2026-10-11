import java.io.File

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
    testImplementation(libs.springSecurityConfig)
    testImplementation(libs.springSecurityWeb)
    testImplementation("org.springframework:spring-test")
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.named("check") {
    dependsOn(tasks.named("checkKotlinAbi"))
}

val updatePerformanceBaseline = providers.gradleProperty("woge.budgets.update").getOrElse("false")

tasks.test {
    systemProperty("woge.budgets.root", rootProject.layout.projectDirectory.asFile.absolutePath)
    systemProperty("woge.budgets.update", providers.gradleProperty("woge.budgets.update").getOrElse("false"))
    inputs.files(rootProject.file("testing/performance/server-policy.json"))
    inputs.files(rootProject.file("testing/performance/spring-mvc-baseline.json"))
    val updatingPerformanceBaseline = updatePerformanceBaseline == "true"
    outputs.upToDateWhen { !updatingPerformanceBaseline }

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
