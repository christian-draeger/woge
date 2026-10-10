package dev.woge.gradle;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

/**
 * Optional Tailwind adapter. It builds {@code /tailwind.css} from {@code src/main/tailwind/tailwind.css}
 * and the class names in your Kotlin code. Plain CSS applications do not need it.
 */
public final class WogeTailwindPlugin implements Plugin<Project> {
    static final String TASK = "wogeTailwind";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(WogeApplicationPlugin.class);
        WogeTailwindExtension extension = project.getExtensions().create(TASK, WogeTailwindExtension.class);
        extension.getInput().convention(project.getLayout().getProjectDirectory().file("src/main/tailwind/tailwind.css"));
        extension.getOutputPath().convention("tailwind.css");
        extension.getExecutor().convention(TailwindExecutor.NPM);
        extension.getNodeProjectDirectory().convention(project.getLayout().getProjectDirectory());
        extension.getCheckDynamicClasses().convention(true);
        project.getPluginManager().withPlugin("org.jetbrains.kotlin.jvm", ignored -> configure(project, extension));
    }

    private void configure(Project project, WogeTailwindExtension extension) {
        SourceSet main = project.getExtensions().getByType(SourceSetContainer.class).getByName("main");
        Directory buildDirectory = project.getLayout().getBuildDirectory().get();
        extension.getSources().convention(project.provider(() -> codeDirectories(main, buildDirectory)));
        TaskProvider<WogeTailwindTask> tailwind = project.getTasks().register(TASK, WogeTailwindTask.class, task -> {
            task.setGroup("build");
            task.setDescription("Builds the application's Tailwind stylesheet.");
            task.getInput().set(extension.getInput());
            task.getSources().from(extension.getSources());
            task.getOutputPath().set(extension.getOutputPath());
            task.getExecutor().set(extension.getExecutor());
            task.getVersion().set(WogeTailwindExtension.SUPPORTED_VERSION);
            task.getCheckDynamicClasses().set(extension.getCheckDynamicClasses());
            task.getNodeProjectDirectory().set(extension.getNodeProjectDirectory());
            task.getExecutable().set(extension.getExecutable());
            task.getLockFile().set(extension.getNodeProjectDirectory().file("package-lock.json")
                    .map(file -> file.getAsFile().isFile() ? file : null));
            task.getToolCache().set(new File(project.getGradle().getGradleUserHomeDir(), "caches/woge/tailwind"));
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("generated/woge-tailwind/resources"));
            task.dependsOn(project.getTasks().matching(candidate -> candidate.getName().equals("kspKotlin")));
        });
        main.getResources().srcDir(tailwind.flatMap(WogeTailwindTask::getOutputDirectory));

        WogeAssetSources.include(project, tailwind.flatMap(generated -> generated.getOutputDirectory().dir("static")));
    }

    private static Set<File> codeDirectories(SourceSet main, Directory buildDirectory) {
        Set<File> directories = new LinkedHashSet<>(main.getAllSource().getSrcDirs());
        directories.removeAll(main.getResources().getSrcDirs());
        directories.removeIf(directory -> directory.toPath().startsWith(buildDirectory.getAsFile().toPath()));
        directories.add(buildDirectory.dir("generated/ksp/main/kotlin").getAsFile());
        return directories;
    }
}
