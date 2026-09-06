plugins {
    application
    id("dev.woge.kotlin-jvm-library")
}

description = "Executable Ktor portability example for Woge."

application {
    mainClass.set("dev.woge.example.ktor.WogeKtorQuickstartKt")
}

dependencies {
    implementation(project(":woge-reference-shared"))
    implementation(project(":woge-ktor"))
    implementation(libs.ktorServerNetty)

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
