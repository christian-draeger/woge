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
    compileOnly(libs.jakartaServletApi)
    compileOnly(libs.springWebflux)

    testImplementation(platform(libs.springBootDependencies))
    testImplementation(libs.springBoot)
    testImplementation(libs.jakartaServletApi)
    testImplementation(libs.springWebflux)
    testImplementation("org.springframework:spring-test")
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
