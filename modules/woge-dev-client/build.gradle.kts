plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Development-only browser client tags that run inside the application child."

dependencies {
    api(project(":woge-core"))
    api(project(":woge-dev-model"))
    implementation(libs.kotlinxSerializationJson)

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
