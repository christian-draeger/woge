plugins {
    id("dev.woge.kotlin-jvm-library")
    alias(libs.plugins.springBoot)
}

description = "Executable Spring Boot WebFlux quickstart for Woge."

dependencies {
    implementation(project(":woge-reference-shared"))
    implementation(project(":woge-spring-boot-starter"))
    implementation(project(":woge-spring-webflux"))
    implementation(libs.springBootStarterWebflux)

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
