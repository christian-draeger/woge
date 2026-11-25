plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("com.google.devtools.ksp") version "2.3.12"
    id("io.quarkus") version "3.40.1.2"
}

val wogeVersion = "0.1.0-quarkus-preview.1"
val wogeRepository = file(
    providers.gradleProperty("wogeRepository").orNull
        ?: error("Set -PwogeRepository to the locally built candidate Maven repository."),
)
require(wogeRepository.isDirectory) { "The candidate Maven repository directory does not exist." }

val embeddedFixtures = layout.projectDirectory.dir("portable")
val gradient =
    if (embeddedFixtures.asFile.isDirectory) embeddedFixtures
    else layout.projectDirectory.dir("../../examples/complexity-gradient")

val portableSources = listOf(
    "dev/woge/examples/gradient/t01staticpage/StaticPage.kt",
    "dev/woge/examples/gradient/t04validation/Notes.kt",
    "dev/woge/examples/gradient/t05enhancedaction/Notes.kt",
    "dev/woge/examples/gradient/t06deferredregion/Forecast.kt",
    "dev/woge/examples/gradient/t07liveupdate/Announcements.kt",
)
val portableAssets = listOf("static-page", "validation", "enhanced-action", "deferred-region", "live-update")

val portablePage = tasks.register<Sync>("portablePage") {
    from(gradient.dir("src/main/kotlin")) { include(portableSources) }
    into(layout.buildDirectory.dir("portable-page"))
}

tasks.register<Zip>("standaloneConsumer") {
    archiveFileName = "woge-quarkus-consumer-$wogeVersion.zip"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    from(layout.projectDirectory) {
        include("settings.gradle.kts", "build.gradle.kts", "README.md", "verify-native.mjs", "src/**")
    }
    from(layout.projectDirectory.dir("../..")) {
        include("gradlew", "gradlew.bat", "gradle/wrapper/**", "LICENSE")
    }
    from(gradient) {
        include(portableSources.map { "src/main/kotlin/$it" })
        include(portableAssets.map { "src/main/resources/gradient/$it/**" })
        into("portable")
    }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
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

val browserAssets by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.40.1.2"))
    implementation("io.quarkus:quarkus-vertx-http")
    implementation("io.quarkus:quarkus-security")
    implementation("dev.woge:woge-quarkus:$wogeVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    ksp("dev.woge:woge-ksp:$wogeVersion")
    browserAssets("dev.woge:woge-fallback-client-assets:$wogeVersion")
    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.quarkus:quarkus-elytron-security-properties-file")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    for (section in portableAssets) {
        from(gradient.dir("src/main/resources/gradient/$section")) {
            into("META-INF/resources/assets/$section")
        }
    }
    from({ browserAssets.map { zipTree(it) } }) {
        include("static/assets/woge/*.js")
        eachFile { relativePath = RelativePath(true, "META-INF", "resources", *relativePath.segments.drop(1).toTypedArray()) }
        includeEmptyDirs = false
    }
}

tasks.test { useJUnitPlatform() }
