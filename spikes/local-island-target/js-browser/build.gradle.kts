@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}
kotlin {
    js(IR) { browser(); binaries.executable(); useEsModules() }
    sourceSets {
        commonMain {
            kotlin.srcDir("../shared")
            dependencies {
                implementation(libs.kotlinxSerializationJson)
                implementation(libs.kotlinxCoroutinesCore)
            }
        }
        jsMain.dependencies { implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0") }
    }
}
