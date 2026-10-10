import opensavvy.gradle.vite.base.tasks.ViteExec

plugins {
    id("dev.opensavvy.vite.base") version "0.9.2"
}

val frontendDirectory = layout.projectDirectory.dir("..")

tasks.register<ViteExec>("viteBuild") {
    command.set("build")
    config.setDefaults()
    config.version.set("8.3.4")
    nodePath.set(File(providers.environmentVariable("WOGE_NODE").get()))
    vitePath.set(frontendDirectory.file("node_modules/vite/bin/vite.js"))
    workingDirectory.set(frontendDirectory.asFile.absolutePath)
    configurationFile.set(frontendDirectory.file("vite.config.mjs"))
    arguments.addAll("--config", frontendDirectory.file("vite.config.mjs").asFile.absolutePath)
}

// Node-free baseline: the same page with plain CSS and browser-native JavaScript modules.
tasks.register<Sync>("gradleOnlyAssets") {
    from(frontendDirectory.dir("src/plain"))
    into(layout.buildDirectory.dir("gradle-only"))
}
