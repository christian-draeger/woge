import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.initialization.resolve.RepositoriesMode

rootProject.name = "woge-application"

pluginManagement {
    val kotlinVersion: String by settings
    val kspVersion: String by settings
    val springBootVersion: String by settings
    val wogeVersion: String by settings

    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("com.google.devtools.ksp") version kspVersion
        id("org.springframework.boot") version springBootVersion
        id("dev.woge.spring-boot") version wogeVersion
    }
    repositories {
        val wogeRepository = providers.gradleProperty("wogeRepository").orNull
        if (wogeRepository != null) {
            maven { url = uri(wogeRepository) }
        } else if (wogeVersion.endsWith("-SNAPSHOT")) {
            maven {
                url = uri("https://central.sonatype.com/repository/maven-snapshots/")
                content { includeGroupByRegex("dev\\.woge(\\..*)?") }
            }
        } else {
            maven {
                name = "githubPackages"
                url = uri("https://maven.pkg.github.com/christian-draeger/woge")
                credentials(PasswordCredentials::class)
                content { includeGroupByRegex("dev\\.woge.*") }
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    val wogeVersion: String by settings
    repositories {
        val wogeRepository = providers.gradleProperty("wogeRepository").orNull
        if (wogeRepository != null) {
            maven { url = uri(wogeRepository) }
        } else if (wogeVersion.endsWith("-SNAPSHOT")) {
            maven {
                url = uri("https://central.sonatype.com/repository/maven-snapshots/")
                content { includeGroup("dev.woge") }
            }
        } else {
            maven {
                name = "githubPackages"
                url = uri("https://maven.pkg.github.com/christian-draeger/woge")
                credentials(PasswordCredentials::class)
                content { includeGroup("dev.woge") }
            }
        }
        mavenCentral()
    }
}
