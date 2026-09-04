plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.detektPlugin)
    implementation(libs.ktlintGradlePlugin)

    testImplementation(gradleTestKit())
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    useJUnitPlatform()
    reports {
        junitXml.required = true
        html.required = true
    }
}
