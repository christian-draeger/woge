package dev.woge.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

/**
 * Host-independent wiring: manifest, assets and the Woge KSP processor. Ktor applications use this
 * plugin directly; together with Gradle's {@code application} plugin it also adds {@code ./gradlew wogeDev}.
 */
public final class WogeApplicationPlugin implements Plugin<Project> {
    static final String KSP_PLUGIN = "com.google.devtools.ksp";

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("org.jetbrains.kotlin.jvm", ignored -> configure(project));
        project.getPluginManager().withPlugin(KSP_PLUGIN, ignored -> project.getDependencies()
                .add("ksp", "dev.woge:woge-ksp:" + WogeSpringBootPlugin.wogeVersion()));
        project.getPluginManager().withPlugin("application", ignored -> {
            if (!project.getPluginManager().hasPlugin("org.springframework.boot")) {
                WogeDevelopmentTasks.register(project, WogeDevelopmentTasks.Host.KTOR, WogeSpringBootPlugin.wogeVersion());
            }
        });
    }

    private void configure(Project project) {
        var output = project.getLayout().getProjectDirectory().file(".woge/manifest.json");
        SourceSet main = project.getExtensions().getByType(SourceSetContainer.class).getByName("main");
        TaskProvider<WogeAssetsTask> assets = project.getTasks().register("wogeAssets", WogeAssetsTask.class, task -> {
            task.setGroup("build");
            task.setDescription("Packages content-hashed static resources and a reproducible asset manifest.");
            var source = project.getLayout().getProjectDirectory().dir("src/main/resources/static");
            task.getSourceDirectory().convention(source);
            task.getSourceFiles().from(project.fileTree(source));
            task.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/woge-assets/resources"));
        });
        main.getOutput().dir(java.util.Map.of("builtBy", assets), assets.flatMap(WogeAssetsTask::getOutputDirectory));
        project.getTasks().named(main.getClassesTaskName(), task -> task.dependsOn(assets));
        TaskProvider<WogeManifestTask> manifest = project.getTasks().register("wogeManifest", WogeManifestTask.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Writes deterministic, non-secret build metadata to .woge/manifest.json.");
            task.getDescriptorClasspath().from(main.getOutput(), project.getConfigurations().getByName("compileClasspath"));
            task.getWogeVersion().convention(WogeSpringBootPlugin.wogeVersion());
            task.getKotlinVersion().convention(project.getProviders().gradleProperty("kotlinVersion"));
            task.getHostAdapter().convention(project.getProviders().gradleProperty("wogeHostAdapter"));
            task.getFrontendMode().convention("server-html");
            task.getCapabilities().convention(java.util.List.of());
            task.getManifestFile().convention(output);
            task.dependsOn(main.getClassesTaskName());
        });
        project.getTasks().matching(task ->
                java.util.Set.of("kspKotlin", "compileKotlin", "compileJava").contains(task.getName()))
                .configureEach(task -> task.doFirst(ignored -> {
                    try {
                        java.nio.file.Files.deleteIfExists(output.getAsFile().toPath());
                    } catch (java.io.IOException error) {
                        throw new java.io.UncheckedIOException(error);
                    }
                }));
        project.getTasks().named("clean", Delete.class, task ->
                task.delete(output));
        project.getTasks().named("build", task -> task.dependsOn(manifest));
    }
}
