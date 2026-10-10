plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Experimental loopback MCP endpoint over the Woge development capabilities (ADR 0075)."

dependencies {
    api(project(":woge-dev-model"))
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.kotlinxSerializationJson)

    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotlinxCoroutinesTest)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
