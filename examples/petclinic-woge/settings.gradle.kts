pluginManagement {
    val wogeRepository = providers.gradleProperty("wogeRepository").get()
    val wogeVersion = providers.gradleProperty("wogeVersion").get()
    repositories {
        exclusiveContent {
            forRepository { maven { url = uri(wogeRepository) } }
            filter { includeGroupByRegex("dev\\.woge(\\..*)?") }
        }
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("jvm") version "2.4.10"
        id("com.google.devtools.ksp") version "2.3.12"
        id("org.springframework.boot") version "4.1.2"
        id("dev.woge.spring-boot") version wogeVersion
    }
}

rootProject.name = "woge-petclinic"
