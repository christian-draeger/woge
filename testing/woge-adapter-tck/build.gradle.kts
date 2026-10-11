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
    implementation(project(":woge-ui-headless"))
    implementation(project(":woge-server-runtime"))
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.kotlinxSerializationJson)
    ksp(project(":woge-ksp"))

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
