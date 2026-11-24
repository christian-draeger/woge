plugins {
    kotlin("jvm") version "2.4.10"
    id("io.quarkus") version "3.40.1.2"
}

val wogeVersion = "0.1.0-beta.1"
val wogeRepository = file(
    providers.gradleProperty("wogeRepository").orNull
        ?: error("Set -PwogeRepository to the extracted beta pilot Maven repository."),
)
require(wogeRepository.isDirectory) { "The beta pilot Maven repository directory does not exist." }

val gradient = layout.projectDirectory.dir("../../examples/complexity-gradient")
val portablePage = tasks.register<Sync>("portablePage") {
    from(gradient.dir("src/main/kotlin")) {
        include("dev/woge/examples/gradient/t01staticpage/StaticPage.kt")
    }
    into(layout.buildDirectory.dir("portable-page"))
}

kotlin {
    jvmToolchain(21)
    sourceSets.named("main") { kotlin.srcDir(portablePage) }
}

repositories {
    exclusiveContent {
        forRepository { maven { url = uri(wogeRepository) } }
        filter { includeGroup("dev.woge") }
    }
    mavenCentral()
}

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.40.1.2"))
    implementation("io.quarkus:quarkus-vertx-http")
    implementation("io.vertx:vertx-lang-kotlin-coroutines")
    implementation("dev.woge:woge-host-spi:$wogeVersion")
    implementation("dev.woge:woge-server-runtime:$wogeVersion")
    testImplementation("io.quarkus:quarkus-junit")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    from(gradient.file("src/main/resources/gradient/static-page/site.css")) {
        into("META-INF/resources/assets/static-page")
    }
}

tasks.test { useJUnitPlatform() }
