rootProject.name = "component-distribution-spike"

include("headless-primitives")
include("binary-components")
include("consumer")

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
