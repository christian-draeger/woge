plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Internal development host: runs the Spring Boot or Ktor application as a managed child process."

dependencies {
    api(project(":woge-dev-orchestrator"))
    implementation(platform(libs.springBootDependencies))
    implementation(libs.springBoot)

    testImplementation(project(":woge-dev-spring-child"))
    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.kotlinCompilerEmbeddable)
    testImplementation(libs.springBootStarterWeb)
    testImplementation(libs.springBootStarterWebflux)
    testImplementation(libs.ktorServerNetty)
    testRuntimeOnly("org.springframework.boot:spring-boot-devtools")
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    systemProperty(
        "woge.dev.test.classpath",
        sourceSets.test
            .get()
            .runtimeClasspath.asPath,
    )
}
