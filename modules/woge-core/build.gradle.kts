import dev.woge.buildlogic.GenerateHtmlElementsTask
import dev.woge.buildlogic.VerifyHtmlElementDatasetTask
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("dev.woge.kotlin-jvm-library")
    alias(libs.plugins.jmh)
}

description = "Web-native HTML, component, and portable value APIs for Woge applications."

val htmlElementDataset =
    rootProject.layout.projectDirectory.file(
        "config/html-elements/webref-elements-2.8.0.tsv",
    )
val generateHtmlElements by tasks.registering(GenerateHtmlElementsTask::class) {
    datasetFile.set(htmlElementDataset)
    outputDirectory.set(layout.buildDirectory.dir("generated/sources/htmlElements/kotlin"))
}
val verifyHtmlElementDataset by tasks.registering(VerifyHtmlElementDatasetTask::class) {
    datasetFile.set(htmlElementDataset)
    expectedSha256 = "96c88d54c0b1dc1801f5ae536cf63cc4a541c33347f87834c76d82354fa33e01"
}

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()
    sourceSets.named("main") {
        kotlin.srcDir(generateHtmlElements)
    }
}

dependencies {
    compileOnlyApi(libs.jetbrainsAnnotations)

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
    dependsOn(verifyHtmlElementDataset)
}
