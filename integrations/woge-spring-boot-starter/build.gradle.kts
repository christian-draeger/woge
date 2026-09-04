plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Spring Boot dependency entry point for Woge applications."

dependencies {
    api(project(":woge-spring-boot-autoconfigure"))
}
