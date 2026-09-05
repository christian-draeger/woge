plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Host-neutral project page used by the executable Woge reference application."

dependencies {
    api(project(":woge-host-spi"))

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
