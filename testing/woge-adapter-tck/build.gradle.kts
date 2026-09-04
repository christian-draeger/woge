plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Reusable framework-neutral contract fixtures for Woge server adapters."

dependencies {
    api(project(":woge-core"))
    api(project(":woge-protocol"))
    api(project(":woge-host-spi"))
    implementation(project(":woge-server-runtime"))
}
