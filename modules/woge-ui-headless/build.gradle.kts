plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Host- and styling-neutral accessible UI primitives for Woge applications."

dependencies {
    api(project(":woge-core"))
}
