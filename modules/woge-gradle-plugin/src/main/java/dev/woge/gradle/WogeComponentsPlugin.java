package dev.woge.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/** Opt-in, offline component catalog and source installer. */
public final class WogeComponentsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        WogeComponentsExtension extension = project.getExtensions().create("wogeComponents", WogeComponentsExtension.class);
        extension.getPackageName().convention("example.components");
        extension.getWogeVersion().convention(pluginVersion());
        register(project, extension, "wogeComponents", "list", "Lists available source-owned components and variants.");
        register(project, extension, "wogeComponentAdd", "add", "Previews or installs normal Kotlin and CSS sources.");
        register(project, extension, "wogeComponentUpdate", "update", "Checks three-way updates; --apply writes only safe changes.");
    }

    private static void register(Project project, WogeComponentsExtension extension, String name, String operation, String description) {
        project.getTasks().register(name, WogeComponentsTask.class, task -> {
            task.setGroup("woge");
            task.setDescription(description);
            task.getOperation().convention(operation);
            task.getComponentName().convention("");
            task.getVariant().convention("css");
            task.getPackageName().convention(extension.getPackageName());
            task.getWogeVersion().convention(extension.getWogeVersion());
            task.getRegistryDirectory().convention(extension.getRegistryDirectory());
            task.getPreview().convention(false);
            task.getApply().convention(false);
            task.getAcceptLocal().convention(false);
            task.getApplicationDirectory().convention(project.getLayout().getProjectDirectory());
            task.getReportDirectory().convention(project.getLayout().getBuildDirectory().dir("woge-components/update"));
        });
    }

    private static String pluginVersion() {
        try (InputStream stream = WogeComponentsPlugin.class.getResourceAsStream("/dev/woge/gradle/woge-gradle-plugin.properties")) {
            if (stream == null) throw new IllegalStateException("Missing Woge plugin version");
            Properties properties = new Properties();
            properties.load(stream);
            return properties.getProperty("version");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
