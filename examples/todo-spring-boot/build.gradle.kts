import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("org.springframework.boot")
    id("dev.woge.spring-boot")
}

group = "example.todo"
version = "0.1.0"

val wogeVersion: String by project
val springBootVersion: String by project

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}

tasks.named<dev.woge.gradle.WogeManifestTask>("wogeManifest") {
    hostAdapter.set("spring-webflux")
    capabilities.set(listOf("pages", "actions", "regions"))
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("dev.woge:woge-spring-boot-starter:$wogeVersion")
    implementation("dev.woge:woge-spring-webflux:$wogeVersion")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("dev.woge:woge-fallback-client-assets:$wogeVersion")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jsoup:jsoup:1.23.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    dependsOn("verifyTodoGuidance")
    useJUnitPlatform()
}

tasks.register("verifyTodoGuidance") {
    group = "verification"
    description = "Checks the application guidance against the checked-in consumer version pins."
    inputs.files("AGENTS.md", "gradle.properties")
    doLast {
        val properties =
            Properties().apply {
                inputs.files
                    .single { it.name == "gradle.properties" }
                    .reader()
                    .use(::load)
            }
        val guidance = inputs.files.single { it.name == "AGENTS.md" }.readText()
        for (key in listOf("wogeVersion", "kotlinVersion", "kspVersion", "springBootVersion")) {
            check("$key=${properties.getProperty(key)}" in guidance) { "AGENTS.md is stale for $key" }
        }
    }
}
