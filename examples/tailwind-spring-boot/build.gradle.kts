import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.google.devtools.ksp")
    id("org.springframework.boot")
    id("dev.woge.spring-boot")
    id("dev.woge.tailwind")
}

group = "example.tailwind"
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

// The defaults are shown for reference. Use `standalone()` to build without Node.js.
wogeTailwind {
    npm()
    input = layout.projectDirectory.file("src/main/tailwind/tailwind.css")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("dev.woge:woge-spring-boot-starter:$wogeVersion")
    implementation("dev.woge:woge-spring-webflux:$wogeVersion")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
}
