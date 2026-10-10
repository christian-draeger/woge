package dev.woge.gradle;

import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.tasks.TaskProvider;

/**
 * Optional Vite adapter (ADR 0070). Production: {@code wogeVite} runs {@code vite build} and its output
 * joins the content-hashed static tree. Development: {@code wogeDev} runs the Vite dev server next to
 * the application. Applications without this plugin never need Node.js.
 */
public final class WogeVitePlugin implements Plugin<Project> {
    static final String TASK = "wogeVite";
    /** Set by {@code wogeDev} for its inner builds, where the Vite dev server replaces {@code vite build}. */
    static final String DEVELOPMENT_PROPERTY = "wogeDevelopment";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(WogeApplicationPlugin.class);
        WogeViteExtension extension = project.getExtensions().create(TASK, WogeViteExtension.class);
        Directory projectDirectory = project.getLayout().getProjectDirectory();
        extension.getFrontendDirectory().convention(projectDirectory.dir("src/main/frontend"));
        extension.getEntries().convention(List.of("main.ts"));
        extension.getOutputPath().convention("vite");
        extension.getNodeProjectDirectory().convention(projectDirectory);
        extension.getNode().convention("node");
        extension.getDevPort().convention(5173);
        extension.getSourceMaps().convention(false);
        project.getPluginManager().withPlugin("org.jetbrains.kotlin.jvm", ignored -> configure(project, extension));
        project.getTasks().withType(WogeDevTask.class).configureEach(task -> {
            task.getViteCommand().set(extension.getNode().map(WogeViteTask::command));
            task.getViteDirectory().set(extension.getNodeProjectDirectory());
            task.getVitePort().set(extension.getDevPort());
            task.getViteEnvironment().set(project.provider(() -> WogeViteTask.environment(
                    extension.getNodeProjectDirectory().get().getAsFile().toPath(),
                    extension.getFrontendDirectory().get().getAsFile().toPath(), "serve")));
        });
    }

    private void configure(Project project, WogeViteExtension extension) {
        TaskProvider<WogeViteTask> vite = project.getTasks().register(TASK, WogeViteTask.class, task -> {
            task.setGroup("build");
            task.setDescription("Builds the Vite frontend into the content-hashed static tree.");
            task.getFrontendDirectory().set(extension.getFrontendDirectory());
            task.getSources().from(extension.getFrontendDirectory());
            task.getNodeProjectDirectory().set(extension.getNodeProjectDirectory());
            List<String> names = new java.util.ArrayList<>(List.of("package.json"));
            names.addAll(WogeViteTask.LOCK_FILES);
            names.addAll(WogeViteTask.CONFIG_FILES);
            // Missing files are fine: Gradle fingerprints them as absent and reruns once they appear.
            names.forEach(name -> task.getConfiguration().from(extension.getNodeProjectDirectory().file(name)));
            task.getEntries().set(extension.getEntries());
            task.getOutputPath().set(extension.getOutputPath().map(WogeViteTask::validOutputPath));
            task.getSourceMaps().set(extension.getSourceMaps());
            task.getNode().set(extension.getNode());
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("generated/woge-vite/static"));
        });
        if (!project.getProviders().gradleProperty(DEVELOPMENT_PROPERTY).isPresent()) {
            WogeAssetSources.include(project, vite.flatMap(WogeViteTask::getOutputDirectory));
        }
    }
}
