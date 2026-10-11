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

    api(project(":woge-server-runtime"))
    implementation(libs.springContext)
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

tasks.register<Test>("nativeDpuBrowserTest") {
    group = "verification"
    description = "Experimental Chromium original-document DPU conformance against the real WebFlux adapter."
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperties(tasks.named<Test>("test").get().systemProperties)
    useJUnitPlatform()
    filter.includeTestsMatching("*native DPU passes original document browser conformance")
    environment("WOGE_DPU_BROWSER_SCRIPT", rootProject.file("client/woge-fallback-client/scripts/test-native-dpu.mjs"))
    inputs.files(
        rootProject.file("client/woge-fallback-client/dist/dpu.js"),
        rootProject.file("client/woge-fallback-client/scripts/test-native-dpu.mjs"),
    )
    testLogging.showStandardStreams = true
}

val updatePerformanceBaseline = providers.gradleProperty("woge.budgets.update").getOrElse("false")

tasks.test {
    systemProperty("woge.budgets.root", rootProject.layout.projectDirectory.asFile.absolutePath)
    systemProperty("woge.budgets.update", providers.gradleProperty("woge.budgets.update").getOrElse("false"))
    inputs.files(rootProject.file("testing/performance/server-policy.json"))
    inputs.files(rootProject.file("testing/performance/spring-webflux-baseline.json"))
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
