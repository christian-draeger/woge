import java.util.Properties

plugins {
    kotlin("jvm") version "2.4.10"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

val checkout = layout.projectDirectory.dir("../..")
val sourceProperties = providers.fileContents(checkout.file("gradle.properties")).asText.map { source ->
    Properties().apply { source.reader().use(::load) }
}
val wogeVersion = sourceProperties.map { it.getProperty("wogeVersion") }
val siteBasePath = providers.gradleProperty("siteBasePath").orElse("/")
val siteRevision = providers.gradleProperty("siteRevision").orElse(
    providers.exec {
        workingDir(checkout)
        commandLine("git", "rev-parse", "HEAD")
    }.standardOutput.asText.map(String::trim),
)
val siteOutput = layout.buildDirectory.dir("site")

dependencies {
    implementation("dev.woge:woge-core:${wogeVersion.get()}")
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.24.0")
    testImplementation("org.jsoup:jsoup:1.23.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

tasks.register<JavaExec>("generateSite") {
    group = "documentation"
    description = "Renders the Woge documentation to standalone static HTML."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.woge.site.GenerateSiteKt"
    inputs.dir(checkout.dir("site/content")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(checkout.dir("site/static")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(
        checkout.asFileTree.matching {
            include("examples/**/src/**/*.kt", "scaffolds/**/src/**/*.kt", "modules/**/src/**/*.kt")
        },
    ).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("siteBasePath", siteBasePath)
    inputs.property("siteVersion", wogeVersion)
    inputs.property("siteRevision", siteRevision)
    outputs.dir(siteOutput)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            checkout.asFile.absolutePath,
            siteOutput.get().asFile.absolutePath,
            siteBasePath.get(),
            wogeVersion.get(),
            siteRevision.get(),
        )
    })
}

tasks.test {
    useJUnitPlatform()
}

tasks.check {
    dependsOn("generateSite")
}
