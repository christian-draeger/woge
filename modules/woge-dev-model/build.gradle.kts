plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Internal transport- and build-tool-independent development lifecycle model."

dependencies {
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
