package dev.woge.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.toolchain.JavaToolchainService;

/**
 * Registers {@code ./gradlew wogeDev} for Spring Boot or Ktor. Both hosts share one launcher; only the
 * build command, the main class source and the restart strategy differ (ADR 0074).
 */
final class WogeDevelopmentTasks {
    static final String TOOLING_CONFIGURATION = "wogeDevTooling";
    static final String RUNTIME_CONFIGURATION = "wogeDevRuntime";
    static final String LAUNCHER_MAIN_CLASS = "dev.woge.development.gradle.WogeDevelopmentMain";

    enum Host {
        SPRING_BOOT("spring-boot"),
        KTOR("ktor");

        final String id;

        Host(String id) {
            this.id = id;
        }
    }

    private WogeDevelopmentTasks() {}

    /** Registers the task, or reconfigures it when another Woge plugin registered it first. */
    static void register(Project project, Host host, String wogeVersion) {
        Configuration tooling = configuration(project, TOOLING_CONFIGURATION,
                "The wogeDev launcher. Never part of the application.", "dev.woge:woge-dev-gradle:" + wogeVersion);
        Action<WogeDevTask> action = task -> configure(project, task, host, tooling, wogeVersion);
        if (project.getTasks().getNames().contains(WogeSpringBootPlugin.DEV_TASK)) {
            project.getTasks().named(WogeSpringBootPlugin.DEV_TASK, WogeDevTask.class, action);
        } else {
            project.getTasks().register(WogeSpringBootPlugin.DEV_TASK, WogeDevTask.class, action);
        }
    }

    private static void configure(Project project, WogeDevTask task, Host host, Configuration tooling,
            String wogeVersion) {
        SourceSet main = project.getExtensions().getByType(SourceSetContainer.class).getByName("main");
        Directory buildDirectory = project.getLayout().getBuildDirectory().get();
        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        List<String> buildFiles = buildFiles(project);
        List<String> innerBuild = innerBuild(project, host);
        Map<String, ?> forwarded = project.getGradle().getStartParameter().getProjectProperties();
        FileCollection childClasspath = main.getRuntimeClasspath();

        task.setGroup("application");
        task.setDescription(WogeWorkflow.DEVELOP.summary());
        task.setClasspath(tooling);
        task.getMainClass().set(LAUNCHER_MAIN_CLASS);
        task.getJavaLauncher().set(toolchains.launcherFor(java.getToolchain()));
        task.getHost().set(host.id);
        task.getProjectDirectory().set(project.getLayout().getProjectDirectory());
        task.getStateDirectory().set(buildDirectory.dir("woge-dev"));
        task.getBuildCommand().set(project.provider(() -> withProperties(innerBuild, forwarded)));
        task.getChildJava().set(toolchains
                .launcherFor(java.getToolchain())
                .map(launcher -> launcher.getExecutablePath().getAsFile().getAbsolutePath()));
        task.getWatchRoots().set(project.provider(() -> watchRoots(main, buildDirectory,
                project.getExtensions().findByType(WogeTailwindExtension.class))));
        task.getBuildFiles().set(buildFiles);
        task.getPort().convention("8080");
        if (host == Host.SPRING_BOOT) {
            task.getMainClassFile().set(buildDirectory.file("resolvedMainClassName"));
            task.getApplicationMainClass().unset();
            task.getFastRestart().convention(true);
        } else {
            // Ktor has no Spring Boot-style development classpath, so the client is added only here.
            childClasspath = childClasspath.plus(configuration(project, RUNTIME_CONFIGURATION,
                    "Development-only additions to the wogeDev child classpath.",
                    "dev.woge:woge-dev-client:" + wogeVersion));
            task.getMainClassFile().set(buildDirectory.file("woge-dev/main-class.txt"));
            task.getApplicationMainClass().set(
                    project.getExtensions().getByType(JavaApplication.class).getMainClass());
            task.getFastRestart().set(false);
        }
        FileCollection classpath = childClasspath;
        task.getChildClasspath().set(project.provider(() -> paths(classpath)));
    }

    private static Configuration configuration(Project project, String name, String description, String dependency) {
        Configuration existing = project.getConfigurations().findByName(name);
        if (existing != null) {
            return existing;
        }
        Configuration created = project.getConfigurations().create(name, configuration -> {
            configuration.setDescription(description);
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.setVisible(false);
        });
        project.getDependencies().add(name, dependency);
        return created;
    }

    private static List<String> innerBuild(Project project, Host host) {
        Project root = project.getRootProject();
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        File wrapper = root.file(windows ? "gradlew.bat" : "gradlew");
        String prefix = project.getPath().equals(":") ? ":" : project.getPath() + ":";
        List<String> command = new ArrayList<>();
        command.add(wrapper.getAbsolutePath());
        command.add("--project-dir");
        command.add(root.getProjectDir().getAbsolutePath());
        command.add("--console=plain");
        if (project.getGradle().getStartParameter().isOffline()) {
            command.add("--offline");
        }
        command.add(prefix + "classes");
        if (host == Host.SPRING_BOOT) {
            command.add(prefix + "resolveMainClassName");
        }
        return command;
    }

    private static List<String> withProperties(List<String> command, Map<String, ?> properties) {
        List<String> result = new ArrayList<>(command);
        properties.forEach((key, value) -> result.add("-P" + key + "=" + value));
        return result;
    }

    private static List<String> buildFiles(Project project) {
        List<String> files = new ArrayList<>();
        for (Project candidate : List.of(project, project.getRootProject())) {
            for (String name : List.of("build.gradle.kts", "build.gradle", "settings.gradle.kts",
                    "settings.gradle", "gradle.properties", "gradle/libs.versions.toml")) {
                String path = candidate.file(name).getAbsolutePath();
                if (!files.contains(path)) {
                    files.add(path);
                }
            }
        }
        return files;
    }

    private static List<String> watchRoots(SourceSet main, Directory buildDirectory, WogeTailwindExtension tailwind) {
        java.nio.file.Path build = buildDirectory.getAsFile().toPath().toAbsolutePath().normalize();
        List<String> roots = new ArrayList<>();
        List<File> directories = new ArrayList<>(main.getAllSource().getSrcDirs());
        if (tailwind != null) {
            directories.add(tailwind.getInput().get().getAsFile().getParentFile());
        }
        for (File directory : directories) {
            java.nio.file.Path path = directory.toPath().toAbsolutePath().normalize();
            if (!path.startsWith(build) && !roots.contains(path.toString())) {
                roots.add(path.toString());
            }
        }
        return roots;
    }

    private static List<String> paths(FileCollection files) {
        List<String> paths = new ArrayList<>();
        files.getFiles().forEach(file -> paths.add(file.getAbsolutePath()));
        return paths;
    }
}
