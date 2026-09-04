plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Framework-neutral page, action, and live-update host ports."

dependencies {
    api(project(":woge-core"))
    api(project(":woge-protocol"))
}
