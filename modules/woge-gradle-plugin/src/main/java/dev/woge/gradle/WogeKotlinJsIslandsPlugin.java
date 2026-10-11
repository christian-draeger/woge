package dev.woge.gradle;

import java.io.File;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

/** Optional integration for production Kotlin/JS IR island assets. */
public final class WogeKotlinJsIslandsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(WogeApplicationPlugin.class);
        WogeIslandsExtension extension = project.getExtensions().create("wogeIslands", WogeIslandsExtension.class);
        project.getPluginManager().withPlugin("org.jetbrains.kotlin.jvm", ignored -> configure(project, extension));
    }

    private void configure(Project project, WogeIslandsExtension extension) {
        TaskProvider<WogeKotlinJsIslandsTask> task = project.getTasks().register("wogeKotlinJsIslands", WogeKotlinJsIslandsTask.class, registered -> {
            registered.setGroup("build");
            registered.setDescription("Packages production Kotlin/JS island modules and generates typed registrations.");
            registered.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("generated/woge-islands"));
            registered.getIslands().set(project.provider(extension::getIslands));
            registered.getModulesDirectory().set(project.getLayout().dir(
                    project.provider(() -> productionOutput(extension.getKotlinJsProject()))));
            if (extension.getKotlinJsProject() != null) {
                registered.dependsOn(extension.getKotlinJsProject().getTasks().matching(candidate ->
                        candidate.getName().equals("jsProductionExecutableCompileSync")));
            }
        });
        var main = project.getExtensions().getByType(SourceSetContainer.class).getByName("main");
        main.getJava().srcDir(task.flatMap(output -> output.getOutputDirectory().dir("java")));
        main.getResources().srcDir(task.flatMap(output -> output.getOutputDirectory().dir("static")));
        project.getTasks().named("compileKotlin", compile -> compile.dependsOn(task));
        project.getTasks().named("processResources", process -> process.dependsOn(task));
    }

    private static File productionOutput(Project moduleProject) {
        if (moduleProject == null) {
            throw new IllegalStateException("Configure wogeIslands.kotlinJs(project, ...) before building island assets");
        }
        return moduleProject.getLayout().getBuildDirectory()
                .dir("compileSync/js/main/productionExecutable/kotlin").get().getAsFile();
    }
}
