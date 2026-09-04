import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("dev.woge.kotlin-jvm-library")
    alias(libs.plugins.jmh)
}

description = "Web-native HTML, component, and portable value APIs for Woge applications."

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    dependsOn(tasks.named("jar"))
    systemProperty("woge.repository.root", rootProject.layout.projectDirectory.asFile.absolutePath)
    systemProperty(
        "woge.core.jar",
        layout.buildDirectory
            .file("libs/${project.name}-${project.version}.jar")
            .get()
            .asFile.absolutePath,
    )
}

jmh {
    jmhVersion.set("1.37")
    profilers.set(listOf("gc"))
    resultFormat.set("JSON")
}

tasks.named("check") {
    dependsOn(tasks.named("jmhClasses"))
}
