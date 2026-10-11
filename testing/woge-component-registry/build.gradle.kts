import groovy.json.JsonSlurper
import org.gradle.api.tasks.PathSensitivity

plugins {
    id("dev.woge.kotlin-jvm-library")
    alias(libs.plugins.kotlinSerialization)
}

description = "Compile and semantic contract tests for every published component variant."

dependencies {
    implementation(project(":woge-ui-headless"))
    implementation(project(":woge-host-spi"))
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

@CacheableTask
abstract class MaterializeRegistry : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val registryDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun materialize() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        val index = JsonSlurper().parse(registryDirectory.file("index.json").get().asFile) as Map<*, *>
        (index["items"] as List<*>).forEach { rawItem ->
            val item = rawItem as Map<*, *>
            (item["variants"] as Map<*, *>).forEach { (variant, rawVariant) ->
                val variantData = rawVariant as Map<*, *>
                listOf("files" to "main", "tests" to "test").forEach { (key, sourceSet) ->
                    (variantData[key] as List<*>).forEach { rawFile ->
                        val file = rawFile as Map<*, *>
                        if (key == "tests" || file["kind"] == "kotlin") {
                            val source = registryDirectory.file(file["source"].toString()).get().asFile
                            val target = output.resolve("$sourceSet/$variant/${source.name}")
                            check(!target.exists()) { "Duplicate registry source: $target" }
                            target.parentFile.mkdirs()
                            target.writeText(
                                source.readText().replace(
                                    "package dev.woge.registry.template",
                                    "package dev.woge.registry.$variant",
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

val materializeRegistry =
    tasks.register<MaterializeRegistry>("materializeRegistry") {
        registryDirectory.set(rootProject.layout.projectDirectory.dir("registry"))
        outputDirectory.set(layout.buildDirectory.dir("generated/registry"))
    }

kotlin.sourceSets.main {
    kotlin.srcDir(materializeRegistry.flatMap { it.outputDirectory.dir("main") })
}
kotlin.sourceSets.test {
    kotlin.srcDir(materializeRegistry.flatMap { it.outputDirectory.dir("test") })
}

val registryTemplates = rootProject.fileTree("registry") { include("**/*.kt") }
tasks.named<org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask>("runKtlintCheckOverMainSourceSet") {
    source(registryTemplates)
}
tasks.named<org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask>("runKtlintFormatOverMainSourceSet") {
    source(registryTemplates)
}
