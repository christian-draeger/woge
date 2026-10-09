plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Internal Spring Boot development host: managed child process with trigger-file restart."

dependencies {
    api(project(":woge-dev-orchestrator"))

    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotlinxCoroutinesTest)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
