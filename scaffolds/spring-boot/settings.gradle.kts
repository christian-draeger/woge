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
        providers.gradleProperty("wogeRepository").orNull?.let { localRepository ->
            maven { url = uri(localRepository) }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        providers.gradleProperty("wogeRepository").orNull?.let { localRepository ->
            maven { url = uri(localRepository) }
        }
        mavenCentral()
    }
}
