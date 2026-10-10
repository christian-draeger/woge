plugins {
    id("dev.woge.kotlin-jvm-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
}

description = "Host-neutral project page used by the executable Woge reference application."

dependencies {
    api(project(":woge-host-spi"))
    implementation(libs.kotlinxSerializationJson)
    runtimeOnly(project(":woge-fallback-client-assets"))
    ksp(project(":woge-ksp"))

    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotlinxCoroutinesTest)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
