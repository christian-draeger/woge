rootProject.name = "spring-html-htmx-baseline"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":shared")
include(":spring-mvc")
include(":spring-webflux")
