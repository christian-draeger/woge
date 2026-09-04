plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Host-neutral document, patch, and live-frame protocol values."

dependencies {
    api(project(":woge-core"))
}
