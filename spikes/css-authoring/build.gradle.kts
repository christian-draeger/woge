import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        explicitApi()
    }
}

dependencies {
    compileOnly("org.jetbrains:annotations:26.1.0")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val negativeFixture = providers.gradleProperty("negativeFixture").orNull
if (negativeFixture != null) {
    require(negativeFixture.matches(Regex("[a-z0-9-]+"))) {
        "negativeFixture must be a lowercase fixture name"
    }
    val fixtureDirectory = layout.projectDirectory.dir("fixtures/negative/$negativeFixture")
    require(fixtureDirectory.asFile.isDirectory) {
        "Unknown negative fixture: $negativeFixture"
    }
    sourceSets.main {
        kotlin.srcDir(fixtureDirectory)
    }
    layout.buildDirectory = layout.projectDirectory.dir("build/negative-$negativeFixture")
}
