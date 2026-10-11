plugins {
    id("dev.woge.kotlin-jvm-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
}

description = "Hand-written baseline for nine application tasks, with deterministic complexity metrics."

dependencies {
    api(project(":woge-host-spi"))
    implementation(project(":woge-spring-webflux"))
    implementation(libs.springContext)
    implementation(libs.kotlinxCoroutinesReactor)
    implementation(libs.kotlinxSerializationJson)
    ksp(project(":woge-ksp"))

    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotlinxCoroutinesTest)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

val updateBaseline = providers.gradleProperty("woge.gradient.update")

tasks.test {
    systemProperty("woge.gradient.root", layout.projectDirectory.asFile.absolutePath)
    updateBaseline.orNull?.let { systemProperty("woge.gradient.update", it) }
    val updating = updateBaseline.isPresent
    outputs.upToDateWhen { !updating }
}
