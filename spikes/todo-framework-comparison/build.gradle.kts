plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("com.google.devtools.ksp") version "2.3.12"
    application
}
kotlin { jvmToolchain(21) }
val wogeVersion = providers.gradleProperty("wogeVersion").getOrElse("0.1.0-SNAPSHOT")
dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("dev.woge:woge-spring-webflux:$wogeVersion")
    implementation("dev.woge:woge-ktor:$wogeVersion")
    runtimeOnly("dev.woge:woge-fallback-client-assets:$wogeVersion")
    ksp("dev.woge:woge-ksp:$wogeVersion")
    implementation("io.ktor:ktor-server-netty-jvm:3.5.2")
    implementation("org.thymeleaf:thymeleaf:3.1.5.RELEASE")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.11.0")
}
kotlin.sourceSets.main {
    kotlin.srcDir("../../examples/todo-spring-boot/src/main/kotlin")
    kotlin.exclude("**/Application.kt", "**/TodoRoutes.kt")
}
application { mainClass.set("experiment.BenchKt") }
val fetchHtmx by tasks.registering(Exec::class) {
    commandLine("python3", "fetch-htmx.py")
    outputs.file("build/generated-resources/static/htmx.min.js")
}
sourceSets.main {
    resources.srcDir("build/generated-resources")
}
val copyCss by tasks.registering(Copy::class) {
    from("../../examples/todo-spring-boot/src/main/resources/static/todo.css")
    into("build/generated-resources/static")
}
tasks.named("processResources") { dependsOn(fetchHtmx, copyCss) }
