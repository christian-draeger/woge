plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Development-only code inside the Spring Boot child: readiness handshake and browser client."

dependencies {
    api(project(":woge-dev-client"))
    // Spring creates Kotlin listeners from spring.factories through Kotlin reflection.
    runtimeOnly(libs.kotlinReflect)
    compileOnly(platform(libs.springBootDependencies))
    compileOnly(libs.springBoot)
}
