plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Internal Spring Boot development host: managed child process with trigger-file restart."

dependencies {
    api(project(":woge-dev-orchestrator"))
    implementation(platform(libs.springBootDependencies))
    implementation(libs.springBoot)

    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.kotlinCompilerEmbeddable)
    testImplementation(libs.springBootStarterWeb)
    testImplementation(libs.springBootStarterWebflux)
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
