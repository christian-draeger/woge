rootProject.name = "woge-docs-site"

includeBuild("../..") {
    dependencySubstitution {
        substitute(module("dev.woge:woge-core")).using(project(":woge-core"))
    }
}
