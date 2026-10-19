plugins {
    kotlin("jvm") version "2.4.10"
    application
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation("com.microsoft.playwright:playwright:1.63.0")
    implementation("it.skrape:skrapeit-html-parser:1.3.0-alpha.2")
}
kotlin.sourceSets.main {
    kotlin.srcDir("build/upstream/core/src/jvmMain/kotlin")
    kotlin.srcDir("build/upstream/core/src/commonMain/kotlin")
    kotlin.exclude("**/LoggerUtils.kt")
}
application { mainClass.set("experiment.JourneysKt") }
tasks.register<Exec>("fetchDsl") {
    commandLine("python3", "fetch-dsl.py")
}
tasks.named("compileKotlin") { dependsOn("fetchDsl") }
tasks.register<JavaExec>("skrapeProbe") {
    dependsOn("classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("experiment.SkrapeProbeKt")
}
