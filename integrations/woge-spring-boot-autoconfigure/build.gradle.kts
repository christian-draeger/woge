plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Conditional Spring Boot wiring and diagnostics for the Woge server adapters."

dependencies {
    api(project(":woge-host-spi"))
    compileOnly(project(":woge-spring-mvc"))
    compileOnly(project(":woge-spring-webflux"))
}
