import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("dev.woge.kotlin-jvm-library")
}

tasks.named<ProcessResources>("processResources") {
    from(rootProject.layout.projectDirectory.dir("client/woge-fallback-client/src")) {
        include("*.js")
        into("static/assets/woge")
    }
}

description = "Host-neutral project page used by the executable Woge reference application."

dependencies {
    api(project(":woge-host-spi"))

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
