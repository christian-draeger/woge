import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.google.devtools.ksp")
    id("org.springframework.boot")
    id("dev.woge.spring-boot")
}

group = "example.woge"
version = "0.1.0-SNAPSHOT"

val wogeVersion: String by project
val kotlinVersion: String by project
val buildJdk: String by project
val jvmTarget: String by project
val springBootVersion: String by project
val wogeSpringAdapter = providers.gradleProperty("wogeSpringAdapter").orElse("webflux").get().lowercase()

require(wogeSpringAdapter in setOf("webflux", "mvc")) {
    "wogeSpringAdapter must be 'webflux' or 'mvc', but was '$wogeSpringAdapter'"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(buildJdk)
    sourceCompatibility = JavaVersion.toVersion(jvmTarget)
    targetCompatibility = JavaVersion.toVersion(jvmTarget)
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.fromTarget(jvmTarget)
    sourceSets.named("main") {
        kotlin.srcDir("src/$wogeSpringAdapter/kotlin")
    }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("dev.woge:woge-spring-boot-starter:$wogeVersion")

    when (wogeSpringAdapter) {
        "webflux" -> {
            implementation("dev.woge:woge-spring-webflux:$wogeVersion")
            implementation("org.springframework.boot:spring-boot-starter-webflux")
        }

        "mvc" -> {
            implementation("dev.woge:woge-spring-mvc:$wogeVersion")
            implementation("org.springframework.boot:spring-boot-starter-web")
        }
    }

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val verifyWogeAgentGuidance =
    tasks.register("verifyWogeAgentGuidance") {
        group = "verification"
        description = "Verifies that AGENTS.md matches the selected Woge application versions and host."
        val guidanceFile = layout.projectDirectory.file("AGENTS.md")
        inputs.file(guidanceFile)
        inputs.properties(
            mapOf(
                "expectedKotlinLine" to "- Kotlin: $kotlinVersion",
                "expectedSpringBootLine" to "- Spring Boot: $springBootVersion",
                "expectedSpringHostLine" to "- Selected host: `$wogeSpringAdapter`",
                "expectedWogeLine" to "- Woge: $wogeVersion",
            ),
        )
        doLast {
            val guidance = inputs.files.singleFile.readText()
            inputs.properties.toSortedMap().values.forEach { expectedValue ->
                val expectedLine = expectedValue.toString()
                check(expectedLine in guidance) {
                    "AGENTS.md is stale: missing '$expectedLine'. Regenerate it with matching Woge scaffold tooling."
                }
            }
        }
    }

tasks.test {
    dependsOn(verifyWogeAgentGuidance)
    useJUnitPlatform()
    systemProperty("woge.expected-adapter", wogeSpringAdapter)
}
