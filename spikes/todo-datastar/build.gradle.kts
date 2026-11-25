plugins {
    kotlin("jvm") version "2.4.10"
    id("org.springframework.boot") version "4.1.1"
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.11.0")
    implementation("org.thymeleaf:thymeleaf:3.1.5.RELEASE")
}

val sharedTodo = layout.projectDirectory.dir("../../examples/todo-spring-boot/src/main")
val copyTodoSource by tasks.registering(Sync::class) {
    from(sharedTodo.dir("kotlin")) { include("example/todo/Todos.kt") }
    into(layout.buildDirectory.dir("generated/shared-todo"))
}
kotlin.sourceSets.named("main") {
    kotlin.srcDir(copyTodoSource)
    if (providers.gradleProperty("todoDeferredStudy").orNull == "true") {
        kotlin.srcDirs("src/deferred/shared", "src/deferred/datastar")
    }
}

tasks.processResources {
    from(sharedTodo.file("resources/static/todo.css")) { into("static") }
}
