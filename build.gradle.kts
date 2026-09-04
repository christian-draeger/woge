import org.gradle.api.tasks.Exec

plugins {
    base
}

group = providers.gradleProperty("wogeGroup").get()
version = providers.gradleProperty("wogeVersion").get()

fun registerValidation(name: String, description: String, script: String) =
    tasks.register<Exec>(name) {
        group = "verification"
        this.description = description
        commandLine("bash", layout.projectDirectory.file(script).asFile.absolutePath)
    }

fun registerAggregate(name: String, description: String, childTaskName: String) =
    tasks.register(name) {
        group = "verification"
        this.description = description
        dependsOn(provider { subprojects.mapNotNull { it.tasks.findByName(childTaskName) } })
    }

val validateAdrs = registerValidation(
    "validateAdrs",
    "Validates ADR names, metadata, lifecycle and local links.",
    "scripts/validate-adrs.sh",
)
val validateModuleBoundaries = registerValidation(
    "validateModuleBoundaries",
    "Validates the machine-readable module boundary manifest.",
    "scripts/validate-module-boundaries.sh",
)
val testModuleBoundaries = registerValidation(
    "testModuleBoundaries",
    "Proves that invalid module graphs and framework leaks are rejected.",
    "scripts/test-module-boundaries.sh",
)
val validateDocumentation = registerValidation(
    "validateDocumentation",
    "Validates local documentation links and executable snippet references.",
    "scripts/validate-documentation.sh",
)

val test = registerAggregate("test", "Runs tests in every production build project.", "test")
val detekt = registerAggregate("detekt", "Runs Detekt in every production build project.", "detekt")
val ktlintCheck = registerAggregate("ktlintCheck", "Checks Kotlin formatting in every production build project.", "ktlintCheck")
registerAggregate("ktlintFormat", "Formats Kotlin source in every production build project.", "ktlintFormat")

tasks.named("check") {
    dependsOn(
        validateAdrs,
        validateModuleBoundaries,
        testModuleBoundaries,
        validateDocumentation,
        test,
        detekt,
        ktlintCheck,
        gradle.includedBuild("build-logic").task(":check"),
    )
}
