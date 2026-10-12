import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.Exec
import org.gradle.jvm.tasks.Jar

plugins {
    base
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

group = providers.gradleProperty("wogeGroup").get()
version = providers.gradleProperty("wogeVersion").get()

val scaffoldPublicationModules =
    setOf(
        "woge-core",
        "woge-ui-headless",
        "woge-protocol",
        "woge-host-spi",
        "woge-server-runtime",
        "woge-spring-mvc",
        "woge-spring-webflux",
        "woge-spring-boot-autoconfigure",
        "woge-spring-boot-starter",
        "woge-ktor",
        "woge-vite",
        "woge-dev-model",
        "woge-dev-orchestrator",
        "woge-dev-client",
        "woge-dev-spring-child",
        "woge-dev-process-host",
        "woge-dev-browser",
        "woge-dev-mcp",
        "woge-dev-gradle",
        "woge-fallback-client-assets",
        "woge-ksp",
    )
val scaffoldPluginModules = setOf("woge-gradle-plugin")
val scaffoldMavenRepository = layout.buildDirectory.dir("scaffold-maven-repository")
val wogeTagline = "HTML-first reactive web development for Kotlin."
val publicationModules = scaffoldPublicationModules + scaffoldPluginModules
val verifyGitHubPackagesPublication = tasks.register<Exec>("verifyGitHubPackagesPublication") {
    group = "publishing"
    description = "Checks explicit channel, credentials and unused release coordinates before any upload."
    commandLine(
        "python3",
        "scripts/verify-github-publication.py",
        "--repository",
        scaffoldMavenRepository.get().asFile.absolutePath,
        "--group",
        project.group.toString(),
        "--version",
        project.version.toString(),
        "--channel",
        providers.gradleProperty("wogePublicationChannel").getOrElse(""),
        "--requested-channel",
        gradle.startParameter.taskNames.mapNotNull {
            when (it.substringAfterLast(':')) {
                "publishGitHubPackagesSnapshot" -> "snapshot"
                "publishGitHubPackagesRelease" -> "release"
                else -> null
            }
        }.distinct().joinToString(","),
    )
}

subprojects {
    if (name in publicationModules) {
        pluginManager.withPlugin("java") {
            extensions.configure<JavaPluginExtension> {
                withSourcesJar()
                withJavadocJar()
            }
            // Kotlin-only modules have no Java Javadoc; ship the actual maintained guides instead.
            tasks.named<Jar>("javadocJar") {
                from(rootProject.layout.projectDirectory.dir("docs")) { into("docs") }
                from(rootProject.layout.projectDirectory.file("README.md"))
                from(rootProject.layout.projectDirectory.file("LICENSE"))
                from(layout.projectDirectory.file("README.md")) { into("module") }
            }
            tasks.named<Jar>("jar") {
                from(rootProject.layout.projectDirectory.file("LICENSE")) { into("META-INF") }
                if (project.name == "woge-core") {
                    from(rootProject.layout.projectDirectory.file("config/html-elements/LICENSE.webref")) {
                        into("META-INF/licenses")
                    }
                    from(rootProject.layout.projectDirectory.file("config/html-elements/README.md")) {
                        into("META-INF/licenses")
                        rename { "webref-provenance.md" }
                    }
                }
            }
        }
        pluginManager.withPlugin("maven-publish") {
            extensions.configure<PublishingExtension> {
                repositories.maven {
                    name = "githubPackages"
                    url = uri("https://maven.pkg.github.com/christian-draeger/woge")
                    credentials(PasswordCredentials::class)
                }
            }
            tasks.withType<PublishToMavenRepository>().configureEach {
                if (name.endsWith("ToGithubPackagesRepository")) {
                    dependsOn(verifyGitHubPackagesPublication)
                }
            }
        }
    }
    afterEvaluate {
        extensions.findByType(PublishingExtension::class.java)?.publications?.withType(MavenPublication::class.java)
            ?.configureEach {
                pom {
                    name.set(project.name)
                    description.set(project.description ?: wogeTagline)
                    url.set("https://github.com/christian-draeger/woge")
                    licenses {
                        license {
                            name.set("Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                            distribution.set("repo")
                        }
                    }
                    scm {
                        url.set("https://github.com/christian-draeger/woge")
                        connection.set("scm:git:https://github.com/christian-draeger/woge.git")
                        developerConnection.set("scm:git:ssh://git@github.com/christian-draeger/woge.git")
                    }
                    developers {
                        developer {
                            id.set("christian-draeger")
                            name.set("Christian Dräger")
                            url.set("https://github.com/christian-draeger")
                        }
                    }
                }
            }
    }
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

// Kotlin configures its Java-compatible sources archive during project evaluation.
// Append distribution sources afterward so every task graph retains them.
gradle.projectsEvaluated {
    subprojects.filter { it.name in publicationModules }.forEach { publishedProject ->
        publishedProject.configurations.findByName("sourcesElements")?.outgoing?.artifacts?.removeIf { artifact ->
            artifact.buildDependencies.getDependencies(null).any { it.name == "kotlinSourcesJar" }
        }
        // Kotlin's check lifecycle also builds this archive; it must not overwrite Java's
        // published sourcesJar at the same default path.
        publishedProject.tasks.withType<Jar>().matching { it.name == "kotlinSourcesJar" }.configureEach {
            destinationDirectory.set(publishedProject.layout.buildDirectory.dir("kotlin-sources"))
        }
        publishedProject.tasks.named<Jar>("sourcesJar") {
            from(publishedProject.layout.projectDirectory.file("build.gradle.kts")) { into("build-definition") }
            from(rootProject.layout.projectDirectory.file("LICENSE"))
            if (publishedProject.name == "woge-core") {
                from(rootProject.layout.projectDirectory.dir("config/html-elements")) {
                    into("provenance/html-elements")
                }
            }
            if (publishedProject.name == "woge-fallback-client-assets") {
                from(rootProject.layout.projectDirectory.dir("client/woge-fallback-client/src")) { into("browser") }
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
verifyGitHubPackagesPublication.configure {
    dependsOn(publishScaffoldArtifacts)
}
val verifyPublicationContract = tasks.register<Exec>("verifyPublicationContract") {
    group = "verification"
    description = "Checks local Maven sources, documentation, POMs, markers and runtime dependency metadata."
    dependsOn(publishScaffoldArtifacts)
    commandLine(
        "python3",
        "scripts/verify-publication-contract.py",
        scaffoldMavenRepository.get().asFile.absolutePath,
        project.group.toString(),
        project.version.toString(),
        publicationModules.sorted().joinToString(","),
    )
}
val testPublicationGuards = tasks.register<Exec>("testPublicationGuards") {
    group = "verification"
    description = "Tests publication channels, credential errors and fail-closed release/check guards without network access."
    commandLine("python3", "-B", "scripts/test-publication-guards.py")
}
for (channel in listOf("snapshot", "release")) {
    tasks.register("publishGitHubPackages${channel.replaceFirstChar { it.uppercase() }}") {
        group = "publishing"
        description = "Explicitly publishes the $channel channel to GitHub Packages."
        dependsOn(
            verifyPublicationContract,
            testPublicationGuards,
            publicationModules.map { ":$it:publishAllPublicationsToGithubPackagesRepository" },
        )
    }
}
verifyGitHubPackagesPublication.configure { dependsOn(verifyPublicationContract) }
val testSpringBootScaffold =
    tasks.register<Exec>("testSpringBootScaffold") {
        group = "verification"
        description = "Materializes and compiles the external Spring Boot scaffold with WebFlux and MVC."
        dependsOn(publishScaffoldArtifacts)
        timeout.set(java.time.Duration.ofMinutes(5))
        commandLine(
            "bash",
            layout.projectDirectory.file("scripts/test-spring-boot-scaffold.sh").asFile.absolutePath,
            scaffoldMavenRepository.get().asFile.absolutePath,
        )
        environment("WOGE_TEST_VERSION", project.version.toString())
    }
val scaffoldDevSmoke =
    tasks.register<Exec>("scaffoldDevSmoke") {
        group = "verification"
        description = "Runs wogeDev in external Spring Boot (WebFlux, MVC) and Ktor apps and checks edit, error and recovery."
        dependsOn(publishScaffoldArtifacts)
        val script = layout.projectDirectory.file("scripts/test-spring-boot-scaffold-dev.sh").asFile.absolutePath
        val ktorScript = layout.projectDirectory.file("scripts/test-ktor-dev.sh").asFile.absolutePath
        val repository = scaffoldMavenRepository.get().asFile.absolutePath
        commandLine(
            "bash",
            "-c",
            "bash \"$1\" \"$2\" webflux && bash \"$1\" \"$2\" mvc && bash \"$3\" \"$2\"",
            "woge-dev-smoke",
            script,
            repository,
            ktorScript,
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
tasks.register<Exec>("testTailwindExample") {
    group = "verification"
    description = "Builds the external Tailwind example with npm and checks the generated, hashed stylesheet."
    dependsOn(publishScaffoldArtifacts)
    commandLine(
        "bash",
        layout.projectDirectory.file("scripts/test-tailwind-example.sh").asFile.absolutePath,
        scaffoldMavenRepository.get().asFile.absolutePath,
    )
}
tasks.register<Exec>("testViteExample") {
    group = "verification"
    description = "Builds the external Vite example with npm and runs it under wogeDev with the Vite dev server."
    dependsOn(publishScaffoldArtifacts)
    commandLine(
        "bash",
        layout.projectDirectory.file("scripts/test-vite-example.sh").asFile.absolutePath,
        scaffoldMavenRepository.get().asFile.absolutePath,
    )
}
val buildReferenceTheme = tasks.register<Exec>("buildReferenceTheme") {
    group = "verification"
    description = "Builds the optional reference theme with the browser job's pinned Tailwind CLI."
    workingDir(layout.projectDirectory.dir("client/woge-fallback-client"))
    commandLine("node", "scripts/build-reference-theme.mjs")
}

val prepareProductionDeployment = tasks.register<Sync>("prepareProductionDeployment") {
    group = "verification"
    description = "Packages the three reference hosts for the opt-in container deployment test."
    dependsOn(
        ":woge-reference-spring-mvc:bootJar",
        ":woge-reference-spring-webflux:bootJar",
        ":woge-reference-ktor:installDist",
    )
    into(layout.buildDirectory.dir("production-deployment"))
    from("testing/deployment/Dockerfile")
    for (host in listOf("spring-mvc", "spring-webflux")) {
        from(project(":woge-reference-$host").layout.buildDirectory.dir("libs")) {
            include("*.jar")
            exclude("*-plain.jar")
            into(host)
            rename { "server.jar" }
        }
    }
    from(project(":woge-reference-ktor").layout.buildDirectory.dir("install/woge-reference-ktor")) {
        into("ktor")
    }
}
val testProductionDeploymentProbe = tasks.register<Exec>("testProductionDeploymentProbe") {
    group = "verification"
    description = "Checks the historical v1 deployment probe reader without Docker or browsers."
    commandLine("python3", "-B", "-m", "unittest", "discover", "-s", "testing/deployment", "-p", "test_*.py")
}
tasks.register<Exec>("productionDeploymentTest") {
    group = "verification"
    description = "Runs real Docker/nginx smoke, resource rejection and rolling shutdown on every reference host."
    dependsOn(prepareProductionDeployment, testProductionDeploymentProbe)
    timeout.set(java.time.Duration.ofMinutes(5))
    commandLine("python3", "testing/deployment/verify.py")
}

fun registerReferenceBrowserSmoke(
    name: String,
    host: String,
    configuration: Exec.() -> Unit = {},
) = tasks.register<Exec>(name) {
    group = "verification"
    description = "Runs the $host reference application in the supported Playwright engines."
    workingDir(layout.projectDirectory.dir("client/woge-fallback-client"))
    dependsOn(buildReferenceTheme)
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

// Framework projects stay Node-free; the opt-in reference rules example uses system Node.
tasks.register<Exec>("performanceBudgets") {
    group = "verification"
    description = "Checks production browser, component and stylesheet byte budgets."
    workingDir(layout.projectDirectory.dir("client/woge-fallback-client"))
    commandLine("npm", "run", "budgets")
    environment("WOGE_BUDGETS_UPDATE", providers.gradleProperty("woge.budgets.update").getOrElse("false"))
}

tasks.named("check") {
    dependsOn(
        ":woge-reference-shared-rules:check",
        validateAdrs,
        validateModuleBoundaries,
        testModuleBoundaries,
        testProductionDeploymentProbe,
        validateDocumentation,
        testSpringBootScaffold,
        verifyPublicationContract,
        test,
        detekt,
        ktlintCheck,
        gradle.includedBuild("build-logic").task(":check"),
    )
}
