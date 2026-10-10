package dev.woge.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * Adds {@code ./gradlew wogeDev} to a Spring Boot application and checks that development tooling
 * never reaches the production jar. Shared wiring such as the manifest and KSP comes from
 * {@link WogeApplicationPlugin}.
 */
public final class WogeSpringBootPlugin implements Plugin<Project> {
    static final String DEV_TASK = "wogeDev";
    static final String VERIFY_TASK = "verifyWogeProductionArtifact";
    static final String HELP_TASK = "wogeTasks";

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("org.springframework.boot", ignored -> configure(project));
    }

    private void configure(Project project) {
        project.getPluginManager().apply(WogeApplicationPlugin.class);
        project.getTasks().withType(WogeManifestTask.class).configureEach(task ->
                task.getHostAdapter().convention(project.getProviders()
                        .gradleProperty("wogeSpringAdapter").map(adapter -> "spring-" + adapter)
                        .orElse(project.getProviders().gradleProperty("wogeHostAdapter"))));
        String wogeVersion = wogeVersion();
        String springBootVersion = springBootVersion(project);

        project.getDependencies().add("developmentOnly", "dev.woge:woge-dev-spring-child:" + wogeVersion);
        project.getDependencies()
                .add("developmentOnly", "org.springframework.boot:spring-boot-devtools:" + springBootVersion);

        WogeDevelopmentTasks.register(project, WogeDevelopmentTasks.Host.SPRING_BOOT, wogeVersion);
        registerProductionCheck(project);
        project.getTasks().register(HELP_TASK, WogeTasksTask.class, task -> {
            task.setGroup("help");
            task.setDescription("Lists the supported Woge workflow. Use --format=json for coding agents.");
            task.getFormat().convention("text");
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
        project.getPluginManager().withPlugin("org.jetbrains.kotlin.jvm", ignored -> {
            TaskProvider<WogeAssetsTask> assets = project.getTasks().named("wogeAssets", WogeAssetsTask.class);
            verify.configure(task -> task.getAssetManifest().set(
                    assets.flatMap(assetTask -> assetTask.getOutputDirectory().file(WogeAssetsTask.MANIFEST))));
        });
    }

    static String wogeVersion() {
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
