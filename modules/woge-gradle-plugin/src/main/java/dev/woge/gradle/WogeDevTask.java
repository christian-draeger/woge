package dev.woge.gradle;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

/**
 * Starts the Woge development session. The task itself does not compile: compile errors belong to the
 * running session, which keeps the last working version online and shows the error in the browser.
 */
@DisableCachingByDefault(because = "Runs an interactive development session")
public abstract class WogeDevTask extends JavaExec {
    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @Internal
    public abstract DirectoryProperty getStateDirectory();

    @Internal
    public abstract ListProperty<String> getBuildCommand();

    @Internal
    public abstract Property<String> getChildJava();

    @Internal
    public abstract ListProperty<String> getChildClasspath();

    @Internal
    public abstract RegularFileProperty getMainClassFile();

    /** The server framework: {@code spring-boot} or {@code ktor}. */
    @Input
    public abstract Property<String> getHost();

    /**
     * The application's main class when the build declares it (Ktor: {@code application.mainClass}).
     * Spring Boot leaves this empty and resolves the main class in every development build instead.
     */
    @Input
    @Optional
    public abstract Property<String> getApplicationMainClass();

    @Internal
    public abstract ListProperty<String> getWatchRoots();

    @Internal
    public abstract ListProperty<String> getBuildFiles();

    /** The fixed local application port. Browsers keep this address across restarts. */
    @Input
    @Option(option = "port", description = "The local application port (default 8080).")
    public abstract Property<String> getPort();

    /** Spring DevTools restarts are faster; a full process restart is always the fallback. Ktor always restarts. */
    @Input
    public abstract Property<Boolean> getFastRestart();

    /** How to start the Vite dev server; empty without the {@code dev.woge.vite} plugin. */
    @Internal
    public abstract ListProperty<String> getViteCommand();

    @Internal
    public abstract MapProperty<String, String> getViteEnvironment();

    @Internal
    public abstract DirectoryProperty getViteDirectory();

    @Input
    @Optional
    public abstract Property<Integer> getVitePort();

    /** Opt-in experimental MCP endpoint for coding agents (ADR 0075). */
    @Input
    public abstract Property<Boolean> getMcp();

    @Option(option = "mcp", description = "Start the experimental MCP endpoint for coding agents.")
    public void setMcpOption(boolean enabled) {
        getMcp().set(enabled);
    }

    /** The MCP port; 0 picks a free port. The URL and token are written to {@code build/woge-dev/mcp.json}. */
    @Input
    @Option(option = "mcp-port", description = "The local MCP port (default: a free port).")
    public abstract Property<String> getMcpPort();

    /** The task that writes {@code .woge/manifest.json}; added to development builds while MCP is on. */
    @Internal
    public abstract Property<String> getManifestTask();

    @Option(option = "full-restart", description = "Always restart the whole application process.")
    public void setFullRestart(boolean fullRestart) {
        getFastRestart().set(!fullRestart);
    }

    @Override
    @TaskAction
    public void exec() {
        Path settings = getStateDirectory().get().file("session.properties").getAsFile().toPath();
        writeMainClass();
        writeSettings(settings);
        args(settings.toString());
        super.exec();
    }

    private void writeSettings(Path file) {
        Properties properties = new Properties();
        properties.setProperty("project", getProjectDirectory().get().getAsFile().getAbsolutePath());
        properties.setProperty("state", getStateDirectory().get().getAsFile().getAbsolutePath());
        properties.setProperty("port", getPort().get());
        properties.setProperty("host", getHost().get());
        properties.setProperty("fastRestart", getFastRestart().get().toString());
        properties.setProperty("pollInterval", "250");
        properties.setProperty("child.java", getChildJava().get());
        properties.setProperty("child.mainClassFile", getMainClassFile().get().getAsFile().getAbsolutePath());
        List<String> buildCommand = new java.util.ArrayList<>(getBuildCommand().get());
        if (getMcp().get() && getManifestTask().isPresent()) {
            buildCommand.add(getManifestTask().get());
        }
        putList(properties, "build.command", buildCommand);
        properties.setProperty("mcp", getMcp().get().toString());
        properties.setProperty("mcp.port", getMcpPort().get());
        putList(properties, "child.classpath", getChildClasspath().get());
        putList(properties, "watch.root", getWatchRoots().get());
        putList(properties, "watch.buildFile", getBuildFiles().get());
        if (!getViteCommand().getOrElse(List.of()).isEmpty()) {
            putList(properties, "vite.command", getViteCommand().get());
            properties.setProperty("vite.directory", getViteDirectory().get().getAsFile().getAbsolutePath());
            properties.setProperty("vite.port", getVitePort().get().toString());
            putList(properties, "vite.env", getViteEnvironment().get().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue()).toList());
        }
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "Generated by the Woge Gradle plugin");
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private void writeMainClass() {
        if (!getApplicationMainClass().isPresent()) {
            return;
        }
        Path file = getMainClassFile().get().getAsFile().toPath();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, getApplicationMainClass().get());
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void putList(Properties properties, String key, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            properties.setProperty(key + "." + index, values.get(index));
        }
    }
}
