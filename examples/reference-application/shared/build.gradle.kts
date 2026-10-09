plugins {
    id("dev.woge.kotlin-jvm-library")
    alias(libs.plugins.ksp)
}

description = "Host-neutral project page used by the executable Woge reference application."

dependencies {
    api(project(":woge-host-spi"))
    runtimeOnly(project(":woge-fallback-client-assets"))
    ksp(project(":woge-ksp"))

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
