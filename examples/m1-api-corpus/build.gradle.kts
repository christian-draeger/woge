plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Compact compile-verified examples and negative compiler fixtures for the public M1 API."

dependencies {
    implementation(project(":woge-core"))
    implementation(project(":woge-protocol"))
    implementation(project(":woge-host-spi"))
    implementation(project(":woge-spring-webflux"))
    implementation(project(":woge-spring-mvc"))
    implementation(project(":woge-ktor"))

    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotlinCompilerEmbeddable)
    testImplementation(libs.kotlinxSerializationJson)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    systemProperty("woge.repository.root", rootProject.layout.projectDirectory.asFile.absolutePath)
}
