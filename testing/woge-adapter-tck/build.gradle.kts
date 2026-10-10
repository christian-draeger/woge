plugins {
    id("dev.woge.kotlin-jvm-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
}

description = "Reusable framework-neutral contract fixtures for Woge server adapters."

dependencies {
    api(project(":woge-core"))
    api(project(":woge-protocol"))
    api(project(":woge-host-spi"))
    implementation(project(":woge-server-runtime"))
    implementation(libs.kotlinxCoroutinesCore)
    ksp(project(":woge-ksp"))
}
