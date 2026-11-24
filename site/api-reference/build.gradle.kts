import java.net.URI
import java.util.Properties

plugins {
    java
    id("org.jetbrains.dokka") version "2.2.0"
}

val checkout = layout.projectDirectory.dir("../..")
val sourceProperties = providers.fileContents(checkout.file("gradle.properties")).asText.map { text ->
    Properties().apply { text.reader().use(::load) }
}
val wogeVersion = providers.gradleProperty("wogeVersion").orElse(
    sourceProperties.map { it.getProperty("wogeVersion") },
)
val revision = providers.exec {
    workingDir(checkout)
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map(String::trim)

val apiModules = listOf(
    "modules/woge-core",
    "modules/woge-host-spi",
    "modules/woge-protocol",
    "modules/woge-ui-headless",
    "adapters/woge-spring-mvc",
    "adapters/woge-spring-webflux",
    "adapters/woge-ktor",
    "integrations/woge-spring-boot-autoconfigure",
)

dependencies {
    for (path in apiModules) {
        implementation("dev.woge:${path.substringAfterLast('/')}:${wogeVersion.get()}")
    }
}

dokka {
    dokkaPublications.html {
        moduleName.set("Woge")
        moduleVersion.set(wogeVersion)
        offlineMode.set(true)
        failOnWarning.set(true)
    }
    dokkaSourceSets.register("wogeJvm") {
        displayName.set("JVM")
        jdkVersion.set(17)
        sourceRoots.from(
            apiModules.map { checkout.dir("$it/src/main/kotlin") },
            checkout.dir("modules/woge-core/build/generated/sources/htmlElements/kotlin"),
        )
        classpath.from(configurations.compileClasspath)
        for (module in apiModules) {
            val sources = "$module/src/main/kotlin"
            sourceLink {
                localDirectory.set(checkout.dir(sources).asFile)
                remoteUrl.set(revision.map { URI("https://github.com/christian-draeger/woge/blob/$it/$sources") })
                remoteLineSuffix.set("#L")
            }
        }
    }
}
