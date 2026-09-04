import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
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

val negativeFixture = providers.gradleProperty("negativeFixture")
if (negativeFixture.isPresent) {
    kotlin.sourceSets.named("main") {
        kotlin.srcDir(layout.projectDirectory.dir("fixtures/negative/${negativeFixture.get()}"))
    }
}

dependencies {
    implementation(project(":headless-primitives"))
    implementation(project(":binary-components"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
