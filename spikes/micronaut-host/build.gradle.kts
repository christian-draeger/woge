plugins {
    application
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("com.google.devtools.ksp") version "2.3.12"
}

val wogeVersion = "0.1.0-beta.1"
val micronautPlatform = "io.micronaut.platform:micronaut-platform:4.10.25"
val wogeRepository = file(
    providers.gradleProperty("wogeRepository").orNull
        ?: error("Set -PwogeRepository to the extracted beta pilot Maven repository."),
)
require(wogeRepository.isDirectory) { "The beta pilot Maven repository directory does not exist." }

val todoSource = file(providers.gradleProperty("wogeTodoSource").getOrElse("../../examples/todo-spring-boot"))
val portableTodo = tasks.register<Sync>("portableTodo") {
    from(todoSource.resolve("src/main/kotlin")) {
        include("example/todo/TodoActions.kt", "example/todo/TodoPage.kt", "example/todo/Todos.kt")
    }
    into(layout.buildDirectory.dir("portable-todo"))
}

kotlin {
    jvmToolchain(21)
    sourceSets.named("main") { kotlin.srcDir(portableTodo) }
}

repositories {
    exclusiveContent {
        forRepository { maven { url = uri(wogeRepository) } }
        filter { includeGroup("dev.woge") }
    }
    mavenCentral()
}

dependencies {
    implementation(platform(micronautPlatform))
    annotationProcessor(platform(micronautPlatform))
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut.security:micronaut-security")
    runtimeOnly("io.micronaut:micronaut-jackson-databind")
    annotationProcessor("io.micronaut:micronaut-inject-java")

    implementation("dev.woge:woge-host-spi:$wogeVersion")
    implementation("dev.woge:woge-server-runtime:$wogeVersion")
    ksp("dev.woge:woge-ksp:$wogeVersion")
    runtimeOnly("dev.woge:woge-fallback-client-assets:$wogeVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("org.jsoup:jsoup:1.23.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

application {
    mainClass = "example.micronaut.TodoController"
}

tasks.processResources {
    from(todoSource.resolve("src/main/resources/static/todo.css")) { into("static") }
}

tasks.test { useJUnitPlatform() }
