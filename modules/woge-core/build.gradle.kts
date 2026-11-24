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

    testImplementation(libs.kotlinCompilerEmbeddable)
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

abstract class FixtureCompilerClasspath : CommandLineArgumentProvider {
    @get:Classpath
    abstract val entries: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> = listOf("-Dwoge.core.fixture.classpath=${entries.asPath}")
}

tasks.test {
    dependsOn(tasks.named("jar"))
    jvmArgumentProviders +=
        objects.newInstance<FixtureCompilerClasspath>().apply {
            entries.from(configurations.compileClasspath)
        }
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

tasks.test {
    systemProperty("woge.xss.corpus", rootProject.file("testing/xss-corpus/payloads.tsv").absolutePath)
}

tasks.named("check") {
    dependsOn(tasks.named("jmhClasses"))
    dependsOn(verifyHtmlElementDataset)
}
