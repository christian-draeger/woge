plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Internal framework-neutral coordinator for the Woge development session."

dependencies {
    api(project(":woge-dev-model"))
    api(libs.kotlinxCoroutinesCore)

    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotlinxCoroutinesTest)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
