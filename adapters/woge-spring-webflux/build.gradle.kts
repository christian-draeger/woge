plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Spring WebFlux transport adapter for Woge."

dependencies {
    api(project(":woge-core"))
    api(project(":woge-protocol"))
    api(project(":woge-host-spi"))
    implementation(project(":woge-server-runtime"))
}
