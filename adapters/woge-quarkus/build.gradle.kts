import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("dev.woge.kotlin-jvm-library")
    `maven-publish`
}

description = "Woge HTTP bindings for the Quarkus-managed Vert.x router."

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()
}

dependencies {
    api(project(":woge-core"))
    api(project(":woge-protocol"))
    api(project(":woge-host-spi"))
    api(project(":woge-server-runtime"))
    api(libs.vertxWeb)
    implementation(libs.vertxKotlinCoroutines)
    implementation(libs.kotlinLoggingJvm)
    implementation(libs.slf4jApi)

    testImplementation(project(":woge-adapter-tck"))
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

publishing {
    publications.register<MavenPublication>("candidate") {
        from(components.getByName("java"))
    }
    repositories.maven {
        name = "candidate"
        url = uri(rootProject.layout.buildDirectory.dir("scaffold-maven-repository"))
    }
}

tasks.named("check") {
    dependsOn(tasks.named("checkKotlinAbi"))
}

tasks.test {
    systemProperty("woge.budgets.root", rootProject.projectDir.absolutePath)
    val updateBudget = providers.gradleProperty("woge.budgets.update").getOrElse("false")
    systemProperty("woge.budgets.update", updateBudget)
    outputs.upToDateWhen { updateBudget != "true" }
    inputs.files(rootProject.file("testing/performance/server-policy.json"))
    inputs.files(rootProject.file("testing/performance/quarkus-baseline.json"))
    for ((key, name) in listOf(
        "nativeFormBrowserScript" to "WOGE_NATIVE_BROWSER_SCRIPT",
        "nativeDpuBrowserScript" to "WOGE_DPU_BROWSER_SCRIPT",
        "streamProxyScript" to "WOGE_STREAM_PROXY_SCRIPT",
    )) {
        val path = providers.environmentVariable(name)
        inputs.property(key, path.orElse(""))
        inputs.files(path.map { listOf(it) }.orElse(emptyList()))
    }
}
