import groovy.json.JsonSlurper
import org.gradle.api.tasks.PathSensitivity

plugins {
    id("dev.woge.kotlin-jvm-library")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
}

description = "Compile and semantic contract tests for every published component variant."

dependencies {
    implementation(project(":woge-ui-headless"))
    implementation(project(":woge-host-spi"))
    ksp(project(":woge-ksp"))
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

val browserFixtures = layout.buildDirectory.dir("generated/browser-components")
val generateBrowserComponents =
    tasks.register<JavaExec>("generateBrowserComponents") {
        dependsOn(tasks.named("classes"))
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("dev.woge.registry.ComponentExamplesKt")
        args(browserFixtures.get().asFile.absolutePath)
        inputs.files(sourceSets.main.get().runtimeClasspath)
        outputs.dir(browserFixtures)
    }

val compileComponentTailwind =
    tasks.register<Exec>("compileComponentTailwind") {
        workingDir(rootProject.file("client/woge-fallback-client"))
        val stylesheet = layout.buildDirectory.file("generated/browser-component-styles/tailwind.css")
        commandLine(
            "node",
            "node_modules/@tailwindcss/cli/dist/index.mjs",
            "-i",
            "browser-tests/components-tailwind.css",
            "-o",
            stylesheet.get().asFile.absolutePath,
            "--minify",
        )
        inputs.file(rootProject.file("client/woge-fallback-client/browser-tests/components-tailwind.css"))
        inputs.file(rootProject.file("client/woge-fallback-client/package-lock.json"))
        inputs.dir(rootProject.file("registry/navigation-components/tailwind"))
        inputs.dir(rootProject.file("registry/overlay-components/tailwind"))
        inputs.dir(rootProject.file("registry/feedback-components/tailwind"))
        inputs.dir(rootProject.file("registry/form-components/tailwind"))
        inputs.dir(rootProject.file("registry/data-display-components/tailwind"))
        inputs.dir(rootProject.file("registry/complex-input-components/tailwind"))
        outputs.file(stylesheet)
    }

tasks.register<Exec>("componentBrowserTest") {
    group = "verification"
    description =
        "Tests source-owned navigation, overlay, feedback, data-display and complex-input contracts in native browsers."
    dependsOn(generateBrowserComponents, compileComponentTailwind)
    workingDir(rootProject.file("client/woge-fallback-client"))
    commandLine(
        "npm",
        "exec",
        "--",
        "playwright",
        "test",
        "--config=playwright.components.config.mjs",
    )
    inputs.dir(rootProject.file("client/woge-fallback-client/browser-tests"))
    inputs.dir(rootProject.file("client/woge-fallback-client/test-support"))
    inputs.dir(rootProject.file("client/woge-fallback-client/dist"))
    inputs.files(
        rootProject.file("client/woge-fallback-client/playwright.config.mjs"),
        rootProject.file("client/woge-fallback-client/playwright.components.config.mjs"),
    )
    inputs.dir(rootProject.file("registry"))
    inputs.dir(rootProject.file("modules/woge-ui-headless/src/main/resources"))
    inputs.dir(browserFixtures)
}
