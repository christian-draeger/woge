import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Exec

plugins {
    base
    alias(libs.plugins.kotlinJvm) apply false
}

group = providers.gradleProperty("wogeGroup").get()
version = providers.gradleProperty("wogeVersion").get()

val scaffoldPublicationModules =
    setOf(
        "woge-core",
        "woge-protocol",
        "woge-host-spi",
        "woge-server-runtime",
        "woge-spring-mvc",
        "woge-spring-webflux",
        "woge-spring-boot-autoconfigure",
        "woge-spring-boot-starter",
        "woge-dev-model",
        "woge-dev-orchestrator",
        "woge-dev-client",
        "woge-dev-spring-child",
        "woge-dev-spring-host",
        "woge-dev-browser",
        "woge-dev-gradle",
    )
val scaffoldPluginModules = setOf("woge-gradle-plugin")
val scaffoldMavenRepository = layout.buildDirectory.dir("scaffold-maven-repository")

subprojects {
    if (name in scaffoldPublicationModules) {
        pluginManager.apply("maven-publish")
        pluginManager.withPlugin("java-library") {
            extensions.configure<PublishingExtension> {
                publications.register<MavenPublication>("scaffold") {
                    from(components.getByName("java"))
                }
                repositories.maven {
                    name = "scaffold"
                    url = uri(scaffoldMavenRepository)
                }
            }
        }
    }
    if (name in scaffoldPluginModules) {
        // java-gradle-plugin publishes the plugin jar and the plugin marker itself.
        pluginManager.apply("maven-publish")
        extensions.configure<PublishingExtension> {
            repositories.maven {
                name = "scaffold"
                url = uri(scaffoldMavenRepository)
            }
        }
    }
}

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
val publishScaffoldArtifacts =
    tasks.register("publishScaffoldArtifacts") {
        group = "publishing"
        description = "Publishes the Woge artifacts needed by the external scaffold to a build-local repository."
        dependsOn(
            scaffoldPublicationModules.map { module ->
                ":$module:publishScaffoldPublicationToScaffoldRepository"
            } +
                scaffoldPluginModules.map { module -> ":$module:publishAllPublicationsToScaffoldRepository" },
        )
    }
val testSpringBootScaffold =
    tasks.register<Exec>("testSpringBootScaffold") {
        group = "verification"
        description = "Materializes and compiles the external Spring Boot scaffold with WebFlux and MVC."
        dependsOn(publishScaffoldArtifacts)
        commandLine(
            "bash",
            layout.projectDirectory.file("scripts/test-spring-boot-scaffold.sh").asFile.absolutePath,
            scaffoldMavenRepository.get().asFile.absolutePath,
        )
    }
val scaffoldDevSmoke =
    tasks.register<Exec>("scaffoldDevSmoke") {
        group = "verification"
        description = "Runs wogeDev in the external Spring Boot scaffold and checks edit, error and recovery."
        dependsOn(publishScaffoldArtifacts)
        commandLine(
            "bash",
            layout.projectDirectory.file("scripts/test-spring-boot-scaffold-dev.sh").asFile.absolutePath,
            scaffoldMavenRepository.get().asFile.absolutePath,
        )
    }
val scaffoldBrowserSmoke =
    tasks.register<Exec>("scaffoldBrowserSmoke") {
        group = "verification"
        description = "Runs the scaffold's focused no-JavaScript browser test."
        dependsOn(publishScaffoldArtifacts)
        commandLine(
            "bash",
            layout.projectDirectory.file("scripts/test-spring-boot-scaffold-browser.sh").asFile.absolutePath,
            scaffoldMavenRepository.get().asFile.absolutePath,
        )
    }
fun registerReferenceBrowserSmoke(
    name: String,
    host: String,
    configuration: Exec.() -> Unit = {},
) = tasks.register<Exec>(name) {
    group = "verification"
    description = "Runs the $host reference application in the supported Playwright engines."
    workingDir(layout.projectDirectory.dir("client/woge-fallback-client"))
    environment("WOGE_REFERENCE_HOST", host)
    commandLine("npm", "run", "test:reference")
    configuration()
}

val referenceBrowserSmokeWebFlux =
    registerReferenceBrowserSmoke("referenceBrowserSmokeWebFlux", "spring-webflux")
val referenceBrowserSmokeMvc =
    registerReferenceBrowserSmoke("referenceBrowserSmokeMvc", "spring-mvc") {
        mustRunAfter(referenceBrowserSmokeWebFlux)
    }
val referenceBrowserSmokeKtor =
    registerReferenceBrowserSmoke("referenceBrowserSmokeKtor", "ktor") {
        mustRunAfter(referenceBrowserSmokeMvc)
    }

tasks.register("referenceBrowserSmoke") {
    group = "verification"
    description = "Runs the shared browser journeys through every maintained server adapter."
    dependsOn(referenceBrowserSmokeWebFlux, referenceBrowserSmokeMvc, referenceBrowserSmokeKtor)
}

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
        testSpringBootScaffold,
        test,
        detekt,
        ktlintCheck,
        gradle.includedBuild("build-logic").task(":check"),
    )
}
