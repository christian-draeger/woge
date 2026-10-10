plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "KSP processor that generates typed Woge region, route and action descriptors."

dependencies {
    implementation(libs.kspApi)

    testImplementation(project(":woge-host-spi"))
    testImplementation(libs.kspAaEmbeddable)
    testImplementation(libs.kspCommonDeps)
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
