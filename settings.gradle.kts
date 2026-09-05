import org.gradle.api.initialization.resolve.RepositoriesMode

rootProject.name = "woge"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

file("config/architecture/module-boundaries.tsv")
    .readLines()
    .asSequence()
    .filterNot { it.isBlank() || it.startsWith("#") }
    .forEach { row ->
        val columns = row.split('\t')
        require(columns.size == 6) { "Invalid module-boundary row: $row" }

        val moduleName = columns[0]
        include(":$moduleName")
        project(":$moduleName").projectDir = file(columns[3])
    }

include(":woge-reference-shared")
project(":woge-reference-shared").projectDir = file("examples/reference-application/shared")

include(":woge-reference-spring-webflux")
project(":woge-reference-spring-webflux").projectDir = file("examples/reference-application/spring-webflux")
