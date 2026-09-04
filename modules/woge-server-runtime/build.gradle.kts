plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Internal shared dispatch and execution implementation for server adapters."

dependencies {
    implementation(project(":woge-core"))
    implementation(project(":woge-protocol"))
    implementation(project(":woge-host-spi"))
    implementation(libs.kotlinxCoroutinesCore)

    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotlinxCoroutinesTest)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
