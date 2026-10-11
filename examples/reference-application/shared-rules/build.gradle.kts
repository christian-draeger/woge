import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

description = "Pure note rules shared by the reference server and its Kotlin/JS island."

kotlin {
    jvmToolchain(21)
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    js(IR) {
        nodejs()
        useEsModules()
        compilerOptions { sourceMap.set(false) }
        binaries.executable()
    }
    sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
}

extensions.configure<NodeJsEnvSpec> { download.set(false) }
rootProject.extensions.configure<NodeJsEnvSpec> { download.set(false) }

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom(files("src/commonMain/kotlin", "src/jsMain/kotlin", "src/commonTest/kotlin"))
}
