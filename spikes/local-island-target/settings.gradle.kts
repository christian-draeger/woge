pluginManagement {
    val catalog = file("../../gradle/libs.versions.toml").readText()
    val kotlinVersion = Regex("(?m)^kotlin = \"([^\"]+)\"").find(catalog)!!.groupValues[1]
    plugins {
        kotlin("multiplatform") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
    }
    repositories { gradlePluginPortal(); mavenCentral() }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}
rootProject.name = "local-island-target"
include("js-direct", "js-browser", "wasm")
