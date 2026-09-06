plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Spring Boot dependency entry point for Woge applications."

dependencies {
    api(platform(libs.springBootDependencies))
    api(project(":woge-spring-boot-autoconfigure"))
}

val verifyNoBundledWebStacks =
    tasks.register("verifyNoBundledWebStacks") {
        group = "verification"
        description = "Verifies that the neutral Woge starter does not transitively bundle a web stack."

        val runtimeClasspath = configurations.named("runtimeClasspath")
        inputs.files(runtimeClasspath)

        doLast {
            val bundledModules =
                inputs.files.files
                    .map { it.name.removeSuffix(".jar") }
                    .toSet()
            val forbidden =
                setOf(
                    "spring-webmvc",
                    "spring-webflux",
                    "woge-spring-mvc",
                    "woge-spring-webflux",
                )
            val bundledWebStacks =
                forbidden.filter { forbiddenName ->
                    bundledModules.any { it.startsWith(forbiddenName) }
                }
            check(bundledWebStacks.isEmpty()) {
                "The neutral Woge starter bundled a web stack: ${bundledWebStacks.sorted()}"
            }
        }
    }

tasks.named("check") {
    dependsOn(verifyNoBundledWebStacks)
}

val verifyNoDevelopmentTooling =
    tasks.register("verifyNoDevelopmentTooling") {
        group = "verification"
        description = "Verifies that production starter dependencies contain no Woge development tooling."

        val runtimeClasspath = configurations.named("runtimeClasspath")
        inputs.files(runtimeClasspath)

        doLast {
            val bundledModules = inputs.files.files.map { it.name.removeSuffix(".jar") }
            check(bundledModules.none { it.startsWith("woge-dev-") }) {
                "The production starter bundled Woge development tooling"
            }
        }
    }

tasks.named("check") {
    dependsOn(verifyNoDevelopmentTooling)
}
