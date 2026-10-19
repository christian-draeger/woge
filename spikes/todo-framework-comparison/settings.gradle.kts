pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        maven { url = uri(providers.gradleProperty("wogeRepository").get()) }
        mavenCentral()
    }
}
rootProject.name = "todo-framework-comparison"
