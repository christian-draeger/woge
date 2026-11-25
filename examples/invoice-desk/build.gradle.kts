plugins {
    application
    id("dev.woge.kotlin-jvm-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
}

description = "Runnable localized invoice intake and review example."

application {
    mainClass.set("dev.woge.examples.invoice.InvoiceDeskKt")
}

dependencies {
    implementation(project(":woge-core"))
    implementation(project(":woge-host-spi"))
    implementation(project(":woge-ktor"))
    implementation(project(":woge-ui-headless"))
    implementation(libs.ktorServerNetty)
    runtimeOnly("com.h2database:h2:2.4.240")
    ksp(project(":woge-ksp"))

    testImplementation(project(":woge-spring-webflux"))
    testImplementation(project(":woge-protocol"))
    testImplementation(libs.jsoup)
    testImplementation(libs.junitJupiter)
    testImplementation(libs.reactorNettyHttp)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    val root = rootProject.layout.projectDirectory
    systemProperty("woge.repository.root", root.asFile.absolutePath)
    inputs.property("invoiceBrowser", providers.environmentVariable("WOGE_INVOICE_BROWSER").orElse("false"))
    inputs.file(root.file("client/woge-fallback-client/scripts/test-saved-invoices.mjs"))
}
