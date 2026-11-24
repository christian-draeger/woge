plugins {
    kotlin("jvm") version "2.4.10"
    id("org.springframework.boot") version "4.1.1"
    id("gg.jte.gradle") version "3.2.5"
}

kotlin { jvmToolchain(21) }

val htmxAsset by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.11.0")
    implementation("gg.jte:jte-runtime:3.2.5")
    compileOnly("gg.jte:jte-kotlin:3.2.5")
    htmxAsset("org.webjars.npm:htmx.org:2.0.11")
}

jte { generate() }

val sharedTodo = layout.projectDirectory.dir("../../examples/todo-spring-boot/src/main")
val copyTodoSource by tasks.registering(Sync::class) {
    from(sharedTodo.dir("kotlin")) { include("example/todo/Todos.kt") }
    into(layout.buildDirectory.dir("generated/shared-todo"))
}
kotlin.sourceSets.named("main") {
    kotlin.srcDir(copyTodoSource)
    kotlin.srcDir(tasks.named("generateJte"))
}

tasks.processResources {
    from(sharedTodo.file("resources/static/todo.css")) { into("static") }
    from({ zipTree(htmxAsset.singleFile) }) {
        include("META-INF/resources/webjars/htmx.org/2.0.11/dist/htmx.min.js")
        eachFile { path = "static/htmx.min.js" }
        includeEmptyDirs = false
    }
}
