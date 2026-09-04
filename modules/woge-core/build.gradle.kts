plugins {
    id("dev.woge.kotlin-jvm-library")
}

description = "Web-native HTML, component, and portable value APIs for Woge applications."

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    dependsOn(tasks.named("jar"))
    systemProperty("woge.repository.root", rootProject.layout.projectDirectory.asFile.absolutePath)
    systemProperty(
        "woge.core.jar",
        layout.buildDirectory
            .file("libs/${project.name}-${project.version}.jar")
            .get()
            .asFile.absolutePath,
    )
}
