import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.google.devtools.ksp")
    id("org.springframework.boot")
    id("dev.woge.spring-boot")
    id("dev.woge.vite")
}

group = "example.vite"
version = "0.1.0-SNAPSHOT"

val wogeVersion: String by project
val buildJdk: String by project
val jvmTarget: String by project
val springBootVersion: String by project

java {
    toolchain.languageVersion = JavaLanguageVersion.of(buildJdk)
    sourceCompatibility = JavaVersion.toVersion(jvmTarget)
    targetCompatibility = JavaVersion.toVersion(jvmTarget)
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.fromTarget(jvmTarget)
}

// The defaults are shown for reference. Vite plugins and options go into vite.config.* as usual.
wogeVite {
    frontendDirectory = layout.projectDirectory.dir("src/main/frontend")
    entries = listOf("main.ts")
    devPort = 5173
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("dev.woge:woge-spring-boot-starter:$wogeVersion")
    implementation("dev.woge:woge-spring-webflux:$wogeVersion")
    implementation("dev.woge:woge-vite:$wogeVersion")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
}
