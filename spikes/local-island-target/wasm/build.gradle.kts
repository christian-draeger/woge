@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}
kotlin {
    wasmJs { browser(); binaries.executable() }
    sourceSets {
        commonMain {
            kotlin.srcDir("../shared")
            dependencies {
                implementation(libs.kotlinxSerializationJson)
                implementation(libs.kotlinxCoroutinesCore)
            }
        }
    }
}
