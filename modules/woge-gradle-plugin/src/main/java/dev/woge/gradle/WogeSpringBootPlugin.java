package dev.woge.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * Adds {@code ./gradlew wogeDev} to a Spring Boot application and checks that development tooling
 * never reaches the production jar.
 */
public final class WogeSpringBootPlugin implements Plugin<Project> {
    static final String DEV_TASK = "wogeDev";
    static final String VERIFY_TASK = "verifyWogeProductionArtifact";
    static final String HELP_TASK = "wogeTasks";
    static final String TOOLING_CONFIGURATION = "wogeDevTooling";
    static final String LAUNCHER_MAIN_CLASS = "dev.woge.development.gradle.WogeDevelopmentMain";

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("org.springframework.boot", ignored -> configure(project));
    }

    private void configure(Project project) {
        String wogeVersion = wogeVersion();
        String springBootVersion = springBootVersion(project);

        project.getDependencies().add("developmentOnly", "dev.woge:woge-dev-spring-child:" + wogeVersion);
        project.getDependencies()
                .add("developmentOnly", "org.springframework.boot:spring-boot-devtools:" + springBootVersion);

        Configuration tooling = project.getConfigurations().create(TOOLING_CONFIGURATION, configuration -> {
            configuration.setDescription("The wogeDev launcher. Never part of the application.");
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.setVisible(false);
        });
        project.getDependencies().add(TOOLING_CONFIGURATION, "dev.woge:woge-dev-gradle:" + wogeVersion);

        registerDevelopmentTask(project, tooling);
        registerProductionCheck(project);
        project.getTasks().register(HELP_TASK, WogeTasksTask.class, task -> {
            task.setGroup("help");
            task.setDescription("Lists the supported Woge workflow. Use --format=json for coding agents.");
            task.getFormat().convention("text");
        });
    }

    private void registerDevelopmentTask(Project project, Configuration tooling) {
        SourceSet main = project.getExtensions().getByType(SourceSetContainer.class).getByName("main");
        FileCollection runtimeClasspath = main.getRuntimeClasspath();
        Directory buildDirectory = project.getLayout().getBuildDirectory().get();
        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        List<String> buildFiles = buildFiles(project);
        List<String> innerBuild = innerBuild(project);
        Map<String, ?> forwarded = project.getGradle().getStartParameter().getProjectProperties();

        project.getTasks().register(DEV_TASK, WogeDevTask.class, task -> {
            task.setGroup("application");
            task.setDescription(WogeWorkflow.DEVELOP.summary());
            task.setClasspath(tooling);
            task.getMainClass().set(LAUNCHER_MAIN_CLASS);
            task.getJavaLauncher().set(toolchains.launcherFor(java.getToolchain()));
            task.getProjectDirectory().set(project.getLayout().getProjectDirectory());
            task.getStateDirectory().set(buildDirectory.dir("woge-dev"));
            task.getBuildCommand().set(project.provider(() -> withProperties(innerBuild, forwarded)));
            task.getChildClasspath().set(project.provider(() -> paths(runtimeClasspath)));
            task.getChildJava().set(toolchains
                    .launcherFor(java.getToolchain())
                    .map(launcher -> launcher.getExecutablePath().getAsFile().getAbsolutePath()));
            task.getMainClassFile().set(buildDirectory.file("resolvedMainClassName"));
            task.getWatchRoots().set(project.provider(() -> watchRoots(main, buildDirectory)));
            task.getBuildFiles().set(buildFiles);
            task.getPort().convention("8080");
            task.getFastRestart().convention(true);
        });
    }

    private void registerProductionCheck(Project project) {
        TaskProvider<Jar> bootJar = project.getTasks().named("bootJar", Jar.class);
        TaskProvider<VerifyWogeProductionArtifact> verify = project.getTasks()
                .register(VERIFY_TASK, VerifyWogeProductionArtifact.class, task -> {
                    task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                    task.setDescription(WogeWorkflow.VERIFY_ARTIFACT.summary());
                    task.getArchive().set(bootJar.flatMap(Jar::getArchiveFile));
                    task.getReport().set(project.getLayout().getBuildDirectory().file("woge/production-artifact.txt"));
                });
        project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME, check -> check.dependsOn(verify));
    }

    private static List<String> innerBuild(Project project) {
        Project root = project.getRootProject();
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        java.io.File wrapper = root.file(windows ? "gradlew.bat" : "gradlew");
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
        command.add(prefix + "resolveMainClassName");
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

    private static List<String> watchRoots(SourceSet main, Directory buildDirectory) {
        java.nio.file.Path build = buildDirectory.getAsFile().toPath().toAbsolutePath().normalize();
        List<String> roots = new ArrayList<>();
        for (java.io.File directory : main.getAllSource().getSrcDirs()) {
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

    private static String wogeVersion() {
        Properties properties = new Properties();
        try (InputStream input = WogeSpringBootPlugin.class.getResourceAsStream("woge-gradle-plugin.properties")) {
            if (input == null) {
                throw new GradleException("The Woge Gradle plugin is missing its version resource.");
            }
            properties.load(input);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return properties.getProperty("version");
    }

    private static String springBootVersion(Project project) {
        Object plugin = project.getPlugins().findPlugin("org.springframework.boot");
        Package bootPackage = plugin == null ? null : plugin.getClass().getPackage();
        String version = bootPackage == null ? null : bootPackage.getImplementationVersion();
        if (version == null) {
            throw new GradleException("Woge could not detect the Spring Boot plugin version.");
        }
        return version;
    }
}
