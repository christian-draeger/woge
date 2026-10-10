plugins {
    `java-gradle-plugin`
}

description = "Gradle plugin for Woge applications: ./gradlew wogeDev and production artifact checks."

group = rootProject.group
version = rootProject.version

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

gradlePlugin {
    plugins {
        register("wogeApplication") {
            id = "dev.woge.application"
            implementationClass = "dev.woge.gradle.WogeApplicationPlugin"
            displayName = "Woge application metadata"
            description = "Generates a deterministic application manifest for any Woge host."
        }
        register("wogeSpringBoot") {
            id = "dev.woge.spring-boot"
            implementationClass = "dev.woge.gradle.WogeSpringBootPlugin"
            displayName = "Woge for Spring Boot"
            description = "Adds ./gradlew wogeDev and keeps development tooling out of production jars."
        }
    }
}

val pluginVersionResource = layout.buildDirectory.dir("generated/woge-plugin-resources")
val generatePluginVersion =
    tasks.register<WriteProperties>("generatePluginVersion") {
        destinationFile = pluginVersionResource.map { it.file("dev/woge/gradle/woge-gradle-plugin.properties") }
        property("version", project.version.toString())
    }

sourceSets.main {
    resources.srcDir(generatePluginVersion.map { pluginVersionResource.get() })
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
