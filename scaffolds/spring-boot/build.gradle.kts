import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.springframework.boot")
}

group = "example.woge"
version = "0.1.0-SNAPSHOT"

val wogeVersion: String by project
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
        kotlin.srcDir(layout.buildDirectory.dir("generated/sources/woge/main/kotlin"))
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

tasks.test {
    useJUnitPlatform()
    systemProperty("woge.expected-adapter", wogeSpringAdapter)
}
