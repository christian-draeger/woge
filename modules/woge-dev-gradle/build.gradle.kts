plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "The wogeDev command: Gradle builds, Spring Boot child and browser channel in one session."

dependencies {
    api(project(":woge-dev-process-host"))
    api(project(":woge-dev-browser"))

    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotlinxCoroutinesTest)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
