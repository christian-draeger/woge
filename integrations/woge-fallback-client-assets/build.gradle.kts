import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.language.jvm.tasks.ProcessResources

abstract class VerifyPatchProtocolAlignment : DefaultTask() {
    @get:InputFile
    abstract val packageMetadata: RegularFileProperty

    @get:InputFile
    abstract val browserVersionSource: RegularFileProperty

    @get:InputFile
    abstract val kotlinPatchSource: RegularFileProperty

    @get:InputFile
    abstract val kotlinPatchStreamSource: RegularFileProperty

    @TaskAction
    fun verify() {
        val packageVersion =
            requireVersion(packageMetadata, "\\\"patchProtocolVersion\\\"\\s*:\\s*(\\d+)", "package metadata")
        val browserVersion =
            requireVersion(browserVersionSource, "WOGE_PATCH_PROTOCOL_VERSION\\s*=\\s*(\\d+)", "browser module")
        val irVersion =
            requireVersion(
                kotlinPatchSource,
                "CURRENT: PatchProtocolVersion = PatchProtocolVersion\\((\\d+)\\)",
                "Patch IR",
            )
        val streamVersion =
            requireVersion(kotlinPatchStreamSource, "const val VERSION: Int = (\\d+)", "patch stream")

        check(setOf(packageVersion, browserVersion, irVersion, streamVersion).size == 1) {
            "Woge patch protocol versions differ: package=$packageVersion, browser=$browserVersion, " +
                "IR=$irVersion, stream=$streamVersion"
        }
    }

    private fun requireVersion(
        source: RegularFileProperty,
        pattern: String,
        label: String,
    ): Int =
        requireNotNull(
            Regex(pattern)
                .find(source.get().asFile.readText())
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull(),
        ) {
            "Could not read patch protocol version from $label"
        }
}

plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Framework-neutral classpath assets for the Woge fallback browser client."

val clientDirectory = rootProject.layout.projectDirectory.dir("client/woge-fallback-client")
val browserSources = clientDirectory.dir("src")
val packageMetadataFile = clientDirectory.file("package.json")
val projectLicense = rootProject.layout.projectDirectory.file("LICENSE")
val kotlinPatch =
    rootProject.layout.projectDirectory.file(
        "modules/woge-protocol/src/main/kotlin/dev/woge/protocol/Patch.kt",
    )
val kotlinPatchStream =
    rootProject.layout.projectDirectory.file(
        "modules/woge-protocol/src/main/kotlin/dev/woge/protocol/PatchStreamValues.kt",
    )

tasks.named<ProcessResources>("processResources") {
    from(browserSources) {
        include("*.js")
        into("static/assets/woge")
    }
    from(browserSources.file("index.d.ts")) {
        into("META-INF/woge/fallback-client")
    }
    from(packageMetadataFile) {
        into("META-INF/woge/fallback-client")
    }
    from(projectLicense) {
        into("META-INF")
    }
}

val verifyPatchProtocolAlignment =
    tasks.register<VerifyPatchProtocolAlignment>("verifyPatchProtocolAlignment") {
        group = "verification"
        description = "Checks browser-package, JVM IR and patch-stream protocol versions agree."
        packageMetadata.set(packageMetadataFile)
        browserVersionSource.set(browserSources.file("version.js"))
        kotlinPatchSource.set(kotlinPatch)
        kotlinPatchStreamSource.set(kotlinPatchStream)
    }

tasks.named("check") {
    dependsOn(verifyPatchProtocolAlignment)
}
