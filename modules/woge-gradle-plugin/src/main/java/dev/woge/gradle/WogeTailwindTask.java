package dev.woge.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Builds the application's Tailwind stylesheet into {@code static/}.
 *
 * <p>Woge writes a small entry file that imports Tailwind with automatic discovery turned off, names
 * every scanned source directory with {@code @source}, and then imports your input. The output is
 * minified and reproducible, so Gradle can cache it.
 */
@CacheableTask
public abstract class WogeTailwindTask extends DefaultTask {
    static final Map<String, String> STANDALONE_SHA256 = Map.of(
            "tailwindcss-macos-arm64", "cdf646702987a743464dff4d9c60fd4480d1c1e73dd819a9a67f1078815dce9d",
            "tailwindcss-macos-x64", "7922e0953f2110c05976e3bf58f14e643d90427575e766b7d433f5f80cbee7e1",
            "tailwindcss-linux-x64", "dc61b3ac6b8c9ca874c0cc4c57b2409791a64c5540404ca5f5367360babc313a",
            "tailwindcss-linux-arm64", "55fd0b241214eff3de1e8ee4f22796662f2d2e7a49bcfca7477cfd0bac398195",
            "tailwindcss-windows-x64.exe", "e0e260ce048014e9268f6237ff18f8ccf02cef521cbd0ae04e82c2cdf7aa3955");

    private static final String UTILITY_PREFIX =
            "(?:[a-z0-9-]+:)*(?:bg|text|border|ring|outline|shadow|fill|stroke|from|via|to|"
                    + "p[trblxyse]?|m[trblxyse]?|gap|w|h|size|min-w|min-h|max-w|max-h|"
                    + "grid-cols|grid-rows|col-span|row-span|rounded|opacity|z|top|right|bottom|left|inset)-";
    private static final Pattern DYNAMIC_CLASS = Pattern.compile(
            "\"[^\"\\n]*?(?<![A-Za-z0-9-])" + UTILITY_PREFIX + "(?:\\$|\"\\s*\\+)");
    private static final Pattern TAILWIND_IMPORT = Pattern.compile("@import\\s+(?:url\\()?[\"']tailwindcss[\"']");

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInput();

    /** Directories Tailwind scans. Missing directories are ignored. */
    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSources();

    @Input
    public abstract Property<String> getOutputPath();

    @Input
    public abstract Property<TailwindExecutor> getExecutor();

    @Input
    public abstract Property<String> getVersion();

    @Input
    public abstract Property<Boolean> getCheckDynamicClasses();

    @Internal
    public abstract DirectoryProperty getNodeProjectDirectory();

    /** The npm lockfile, so a changed Tailwind install builds again. */
    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getLockFile();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getExecutable();

    /** Where downloaded standalone executables are kept between builds. */
    @Internal
    public abstract DirectoryProperty getToolCache();

    /** Root of the generated resources; the stylesheet is written below {@code static/}. */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() throws IOException, InterruptedException {
        Path input = getInput().get().getAsFile().toPath();
        Path project = getNodeProjectDirectory().get().getAsFile().toPath();
        List<Path> sources = existingDirectories();
        if (getCheckDynamicClasses().get()) checkDynamicClasses(sources, project);
        String css = Files.readString(input, StandardCharsets.UTF_8);
        if (TAILWIND_IMPORT.matcher(css.replaceAll("(?s)/\\*.*?\\*/", "")).find()) {
            throw new GradleException(input + ": remove `@import \"tailwindcss\"`. Woge adds it with "
                    + "automatic source detection off and adds an @source line for every scanned directory.");
        }
        Path executable = executable(project);
        Path entry = getTemporaryDir().toPath().resolve("woge-tailwind-entry.css");
        Files.writeString(entry, entry(input, sources, project), StandardCharsets.UTF_8);
        Path outputRoot = getOutputDirectory().get().getAsFile().toPath();
        deleteContents(outputRoot);
        Path output = outputRoot.resolve("static").resolve(relativeOutputPath());
        Files.createDirectories(output.getParent());
        run(List.of(executable.toString(), "--input", entry.toString(), "--output", output.toString(), "--minify"),
                project);
    }

    private List<Path> existingDirectories() {
        return getSources().getFiles().stream()
                .map(file -> file.toPath().toAbsolutePath().normalize())
                .filter(Files::isDirectory)
                .distinct()
                .sorted()
                .toList();
    }

    private Path relativeOutputPath() {
        Path relative = Path.of(getOutputPath().get()).normalize();
        if (relative.isAbsolute() || relative.startsWith("..") || !relative.toString().endsWith(".css")) {
            throw new GradleException("wogeTailwind.outputPath must be a relative .css path below static/, but was "
                    + getOutputPath().get());
        }
        return relative;
    }

    private String entry(Path input, List<Path> sources, Path project) {
        StringBuilder css = new StringBuilder();
        if (getExecutor().get() == TailwindExecutor.NPM && !getExecutable().isPresent()) {
            css.append("@import ").append(quoted(project.resolve("node_modules/tailwindcss/index.css")))
                    .append(" source(none);\n");
        } else {
            css.append("@import \"tailwindcss\" source(none);\n");
        }
        css.append("@import ").append(quoted(input.toAbsolutePath())).append(";\n");
        for (Path source : sources) css.append("@source ").append(quoted(source)).append(";\n");
        return css.toString();
    }

    private static String quoted(Path path) {
        String value = path.toString().replace('\\', '/');
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    static List<String> dynamicClasses(Path file, Path root) throws IOException {
        List<String> findings = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).strip();
            if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) continue;
            if (DYNAMIC_CLASS.matcher(line).find()) {
                findings.add(root.relativize(file).toString().replace('\\', '/') + ":" + (index + 1) + ": " + line);
            }
        }
        return findings;
    }

    private static void checkDynamicClasses(List<Path> sources, Path project) throws IOException {
        List<String> findings = new ArrayList<>();
        for (Path source : sources) {
            try (Stream<Path> files = Files.walk(source)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".kt")).sorted().toList()) {
                    findings.addAll(dynamicClasses(file, project));
                }
            }
        }
        if (findings.isEmpty()) return;
        throw new GradleException("Tailwind cannot see class names that are built at runtime:\n  "
                + String.join("\n  ", findings)
                + "\nWrite each class name in full, for example `when (tone) { Tone.INFO -> \"bg-sky-500\" ... }`, "
                + "or list the names with `@source inline(\"...\")` in your Tailwind input. "
                + "To turn this check off, set `wogeTailwind { checkDynamicClasses = false }`.");
    }

    private Path executable(Path project) throws IOException, InterruptedException {
        if (getExecutable().isPresent()) return getExecutable().get().getAsFile().toPath();
        if (getExecutor().get() == TailwindExecutor.STANDALONE) return standalone();
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path cli = project.resolve("node_modules/.bin/" + (windows ? "tailwindcss.cmd" : "tailwindcss"));
        if (!Files.isRegularFile(cli)) {
            throw new GradleException("Tailwind is not installed in " + project + ". Run `npm install --save-dev "
                    + "--save-exact tailwindcss@" + getVersion().get() + " @tailwindcss/cli@" + getVersion().get()
                    + "` (or `npm ci`), or use `wogeTailwind { standalone() }` to build without Node.js.");
        }
        Path manifest = project.resolve("node_modules/@tailwindcss/cli/package.json");
        if (Files.isRegularFile(manifest)) {
            Matcher version = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(Files.readString(manifest, StandardCharsets.UTF_8));
            if (version.find() && !version.group(1).equals(getVersion().get())) {
                getLogger().warn("Woge is tested with Tailwind {}, but {} is installed.", getVersion().get(),
                        version.group(1));
            }
        }
        return cli;
    }

    static String standaloneAsset(String osName, String architecture) {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = architecture.toLowerCase(Locale.ROOT);
        boolean arm = arch.equals("aarch64") || arch.equals("arm64");
        boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
        if (os.contains("mac") && (arm || x64)) return "tailwindcss-macos-" + (arm ? "arm64" : "x64");
        if (os.contains("linux") && (arm || x64)) return "tailwindcss-linux-" + (arm ? "arm64" : "x64");
        if (os.contains("win") && x64) return "tailwindcss-windows-x64.exe";
        throw new GradleException("No pinned Tailwind standalone executable for " + osName + "/" + architecture
                + ". Use the npm executor or set wogeTailwind.executable.");
    }

    private Path standalone() throws IOException, InterruptedException {
        String asset = standaloneAsset(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
        String version = getVersion().get();
        if (!version.equals(WogeTailwindExtension.SUPPORTED_VERSION)) {
            throw new GradleException("The standalone executor only has a pinned checksum for Tailwind "
                    + WogeTailwindExtension.SUPPORTED_VERSION + ".");
        }
        String expected = STANDALONE_SHA256.get(asset);
        Path target = getToolCache().get().getAsFile().toPath().resolve(version).resolve(asset);
        if (Files.isRegularFile(target) && sha256(target).equals(expected)) return target;
        Files.createDirectories(target.getParent());
        Path download = Files.createTempFile(target.getParent(), asset, ".download");
        URI uri = URI.create("https://github.com/tailwindlabs/tailwindcss/releases/download/v" + version + "/" + asset);
        getLogger().lifecycle("Downloading Tailwind {} standalone executable from {}", version, uri);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<Path> response = client.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).build(),
                    HttpResponse.BodyHandlers.ofFile(download));
            if (response.statusCode() != 200) {
                throw new GradleException("Could not download " + uri + ": HTTP " + response.statusCode());
            }
            String actual = sha256(download);
            if (!actual.equals(expected)) {
                throw new GradleException("Checksum mismatch for " + uri + ": expected " + expected + ", got " + actual);
            }
            if (!download.toFile().setExecutable(true)) {
                throw new GradleException("Could not make " + download + " executable");
            }
            Files.move(download, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            throw new GradleException("Could not download the Tailwind standalone executable from " + uri
                    + ". Check your network, or use the npm executor or wogeTailwind.executable.", error);
        } finally {
            Files.deleteIfExists(download);
        }
        return target;
    }

    static String sha256(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private void run(List<String> command, Path directory) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exit = process.waitFor();
        if (exit != 0) {
            throw new GradleException("Tailwind failed with exit code " + exit + ":\n" + output.strip());
        }
        getLogger().info(output);
    }

    private static void deleteContents(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                if (!path.equals(directory)) {
                    try {
                        Files.delete(path);
                    } catch (IOException error) {
                        throw new UncheckedIOException(error);
                    }
                }
            }
        }
    }
}
