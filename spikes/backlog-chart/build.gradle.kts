plugins {
    application
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("com.google.devtools.ksp") version "2.3.12"
}

val pilot = file(providers.gradleProperty("wogeRepository").get())
require(pilot.isDirectory) { "Set -PwogeRepository to the fixed beta pilot Maven repository." }

repositories {
    exclusiveContent {
        forRepository { maven { url = uri(pilot) } }
        filter { includeGroup("dev.woge") }
    }
    mavenCentral()
}

dependencies {
    implementation("dev.woge:woge-ktor:0.1.0-beta.1")
    ksp("dev.woge:woge-ksp:0.1.0-beta.1")
    runtimeOnly("dev.woge:woge-fallback-client-assets:0.1.0-beta.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

kotlin { jvmToolchain(21) }
application { mainClass = "example.backlog.BacklogServerKt" }

val islandAssets = layout.buildDirectory.dir("island-assets")
val bundleWidget = tasks.register<Exec>("bundleWidget") {
    inputs.dir("widget")
    inputs.files("package.json", "package-lock.json")
    outputs.dir(islandAssets)
    commandLine("npm", "run", "build")
}

tasks.processResources {
    dependsOn(bundleWidget)
    from(islandAssets)
}
