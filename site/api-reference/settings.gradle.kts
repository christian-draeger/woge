pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "woge-api-reference"
includeBuild("../..")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
