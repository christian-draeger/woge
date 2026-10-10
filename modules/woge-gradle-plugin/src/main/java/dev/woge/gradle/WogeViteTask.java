package dev.woge.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Runs the project's own {@code vite build}. The output lands below {@code static/<outputPath>} with
 * stable names, so {@code wogeAssets} content-hashes it like every other static file (ADR 0070).
 */
@CacheableTask
public abstract class WogeViteTask extends DefaultTask {
    static final List<String> CONFIG_FILES = List.of(
            "vite.config.js", "vite.config.mjs", "vite.config.cjs", "vite.config.ts", "vite.config.mts", "vite.config.cts");
    static final List<String> LOCK_FILES = List.of(
            "package-lock.json", "npm-shrinkwrap.json", "pnpm-lock.yaml", "yarn.lock", "bun.lock");

    /** Everything in the frontend folder. Kotlin sources are never Vite inputs. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSources();

    /** {@code package.json}, the lockfile and the Vite config, so a changed install or config builds again. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getConfiguration();

    @Internal
    public abstract DirectoryProperty getFrontendDirectory();

    @Internal
    public abstract DirectoryProperty getNodeProjectDirectory();

    @Input
    public abstract ListProperty<String> getEntries();

    @Input
    public abstract Property<String> getOutputPath();

    @Input
    public abstract Property<Boolean> getSourceMaps();

    /** Machine-specific, so it does not affect the build cache key. */
    @Internal
    public abstract Property<String> getNode();

    /** Root of the generated static tree; Vite writes below {@code <outputPath>}. */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /** Part of the cache key, so a new Woge version with different Vite options builds again. */
    @Input
    public String getScript() {
        return script();
    }

    @TaskAction
    public void build() throws IOException, InterruptedException {
        Path project = getNodeProjectDirectory().get().getAsFile().toPath();
        Path frontend = getFrontendDirectory().get().getAsFile().toPath();
        for (String entry : getEntries().get()) {
            if (!Files.isRegularFile(frontend.resolve(entry))) {
                throw new GradleException("The Vite entry " + frontend.resolve(entry) + " does not exist. Create it or "
                        + "change `wogeVite { entries = listOf(...) }`.");
            }
        }
        Path outputRoot = getOutputDirectory().get().getAsFile().toPath();
        deleteContents(outputRoot);
        Map<String, String> environment = environment(project, frontend, "build");
        environment.put("WOGE_VITE_ENTRIES", String.join(",", getEntries().get()));
        environment.put("WOGE_VITE_OUT_DIR", outputRoot.resolve(getOutputPath().get()).toString());
        environment.put("WOGE_VITE_SOURCEMAP", getSourceMaps().get().toString());
        run(command(getNode().get()), environment, project);
    }

    static List<String> command(String node) {
        return List.of(node, "--input-type=module", "--eval", script());
    }

    static Map<String, String> environment(Path project, Path frontend, String command) {
        Map<String, String> environment = new java.util.LinkedHashMap<>();
        environment.put("WOGE_VITE_COMMAND", command);
        environment.put("WOGE_VITE_ROOT", frontend.toAbsolutePath().toString());
        environment.put("WOGE_VITE_CONFIG", configFile(project).map(Path::toString).orElse(""));
        return environment;
    }

    static Optional<Path> configFile(Path project) {
        return CONFIG_FILES.stream().map(project::resolve).filter(Files::isRegularFile).findFirst();
    }

    static String script() {
        try (InputStream input = WogeViteTask.class.getResourceAsStream("woge-vite.mjs")) {
            if (input == null) throw new IllegalStateException("woge-vite.mjs is missing from the Woge Gradle plugin");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    static String validOutputPath(String value) {
        if (!value.matches("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*")) {
            throw new GradleException("wogeVite.outputPath must be a relative folder such as `vite`, but was " + value);
        }
        return value;
    }

    private void run(List<String> command, Map<String, String> environment, Path directory)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process;
        try {
            process = builder.start();
        } catch (IOException error) {
            throw new GradleException("Could not start Node.js (`" + command.get(0) + "`). Install Node.js, or set "
                    + "`wogeVite { node = \"/path/to/node\" }`.", error);
        }
        try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
            reader.lines().forEach(getLogger()::lifecycle);
        }
        int exit = process.waitFor();
        if (exit != 0) {
            throw new GradleException("vite build failed with exit code " + exit + ". See the output above.");
        }
    }

    private static void deleteContents(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                if (!path.equals(directory)) Files.delete(path);
            }
        }
    }
}
