plugins {
    id("dev.woge.kotlin-jvm-library")
    alias(libs.plugins.springBoot)
}

description = "Executable Spring Boot MVC quickstart for Woge."

dependencies {
    implementation(project(":woge-reference-shared"))
    implementation(project(":woge-spring-boot-starter"))
    implementation(project(":woge-spring-mvc"))
    implementation(libs.springBootStarterWeb)

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
