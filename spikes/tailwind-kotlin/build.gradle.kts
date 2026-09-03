import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        explicitApi()
    }
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val generatedDescriptorDirectory = layout.buildDirectory.dir("generated/sources/woge")
val generatedResourceDirectory = layout.buildDirectory.dir("generated-resources/main")
val generatedDescriptor = generatedDescriptorDirectory.map {
    it.file("dev/woge/generated/ProjectRegionStyles.kt")
}

val generateWogeDescriptors by tasks.registering {
    outputs.file(generatedDescriptor)
    doLast {
        val output = generatedDescriptor.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            """
            package dev.woge.generated

            public object ProjectRegionStyles {
                public const val pending: String = "animate-pulse ring-2 ring-brand-500"
                public const val ready: String = "opacity-100 transition-opacity"
            }
            """.trimIndent() + "\n",
        )
    }
}

sourceSets.main {
    kotlin.srcDir(generatedDescriptorDirectory)
    resources.srcDir(generatedResourceDirectory)
}

tasks.compileKotlin {
    dependsOn(generateWogeDescriptors)
}

val inputCss = layout.projectDirectory.file("src/main/css/tailwind.css")
val applicationCss = layout.projectDirectory.file("src/main/css/application.css")
val cli = layout.projectDirectory.file("node_modules/@tailwindcss/cli/dist/index.mjs")

fun Exec.configureTailwind(outputName: String, minify: Boolean) {
    dependsOn(generateWogeDescriptors)
    inputs.file(inputCss)
    inputs.file(layout.projectDirectory.file("package-lock.json"))
    inputs.file(layout.projectDirectory.file("build-tailwind.mjs"))
    inputs.files(fileTree("src/main/kotlin") { include("**/*.kt") })
    inputs.files(fileTree("fixtures/source-distributed") { include("**/*.kt") })
    inputs.file(generatedDescriptor)

    val outputDirectory = layout.buildDirectory.dir(if (minify) "tailwind-production" else "tailwind-dev")
    val outputCss = outputDirectory.map { it.file("$outputName.css") }
    val outputMap = outputDirectory.map { it.file("$outputName.css.map") }
    outputs.files(outputCss, outputMap)

    doFirst {
        check(cli.asFile.isFile) { "Run npm ci in spikes/tailwind-kotlin before the Gradle CSS task" }
        outputCss.get().asFile.parentFile.mkdirs()
    }

    val arguments = mutableListOf(
        "node",
        "build-tailwind.mjs",
        "--input",
        "src/main/css/tailwind.css",
        "--output",
        outputCss.get().asFile.relativeTo(layout.projectDirectory.asFile).path,
    )
    if (minify) arguments += "--minify"
    commandLine(arguments)
}

val tailwindCss by tasks.registering(Exec::class) {
    group = "build"
    description = "Build readable Tailwind CSS and a source map"
    configureTailwind("tailwind", minify = false)
}

val tailwindCssProduction by tasks.registering(Exec::class) {
    group = "build"
    description = "Build reproducible minified Tailwind CSS and a source map"
    configureTailwind("tailwind.min", minify = true)
}

val assembleStyleAssets by tasks.registering(Sync::class) {
    group = "build"
    description = "Assemble byte-preserved application CSS and generated Tailwind assets"
    dependsOn(tailwindCssProduction)
    from(applicationCss)
    from(layout.buildDirectory.dir("tailwind-production"))
    into(generatedResourceDirectory.map { it.dir("static/assets") })
}

val validateTailwindCandidates by tasks.registering(Exec::class) {
    group = "verification"
    description = "Reject dynamically assembled Tailwind utility names in scanned Kotlin source"
    commandLine(
        "node",
        "candidate-policy.mjs",
        "src/main/kotlin",
        "fixtures/source-distributed",
    )
}

val tailwindCssWatch by tasks.registering(Exec::class) {
    group = "development"
    description = "Continuously rebuild CSS; use the application dev server for browser reload/HMR"
    dependsOn(generateWogeDescriptors)
    doFirst {
        check(cli.asFile.isFile) { "Run npm ci in spikes/tailwind-kotlin before the Gradle CSS task" }
        layout.buildDirectory.dir("tailwind-watch").get().asFile.mkdirs()
    }
    commandLine(
        "node",
        cli.asFile.absolutePath,
        "--input",
        inputCss.asFile.absolutePath,
        "--output",
        layout.buildDirectory.file("tailwind-watch/application.css").get().asFile.absolutePath,
        "--map",
        "--watch=always",
        "--poll=100",
    )
}

tasks.processResources {
    dependsOn(assembleStyleAssets)
}

tasks.check {
    dependsOn(validateTailwindCandidates, tailwindCss, tailwindCssProduction)
}
