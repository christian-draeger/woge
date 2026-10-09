
dependencies {
    developmentOnly("org.springframework.boot:spring-boot-devtools")
}

val reloadProbeClasspath = layout.buildDirectory.file("reload-probe/runtime-classpath.txt")

tasks.register("writeReloadProbeRuntimeClasspath") {
    dependsOn(tasks.named("classes"))
    outputs.file(reloadProbeClasspath)
    doLast {
        val output = reloadProbeClasspath.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            sourceSets["main"].runtimeClasspath.files
                .joinToString(File.pathSeparator) { it.absolutePath },
        )
    }
}
