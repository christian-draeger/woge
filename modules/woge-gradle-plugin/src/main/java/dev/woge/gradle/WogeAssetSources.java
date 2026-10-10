package dev.woge.gradle;

import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;

/**
 * Lets optional adapters (Tailwind, Vite) add generated files to the static tree that {@code wogeAssets}
 * content-hashes. Plain applications never get this extra copy step.
 */
final class WogeAssetSources {
    static final String TASK = "wogeAssetSources";

    private WogeAssetSources() {}

    /** Adds a generated directory whose layout mirrors {@code src/main/resources/static}. */
    static void include(Project project, Provider<Directory> generatedStaticDirectory) {
        if (!project.getTasks().getNames().contains(TASK)) {
            var staticDirectory = project.getLayout().getProjectDirectory().dir("src/main/resources/static");
            var merged = project.getLayout().getBuildDirectory().dir("generated/woge-asset-sources");
            var sync = project.getTasks().register(TASK, Sync.class, task -> {
                task.setDescription("Collects static files and generated frontend files for content hashing.");
                task.from(staticDirectory);
                task.into(merged);
            });
            project.getTasks().named("wogeAssets", WogeAssetsTask.class, task -> {
                task.getSourceDirectory().set(merged);
                task.getSourceFiles().setFrom(project.fileTree(merged).builtBy(sync));
            });
        }
        project.getTasks().named(TASK, Sync.class, task -> task.from(generatedStaticDirectory));
    }
}
