package dev.woge.gradle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class WogeKotlinJsIslandsTask extends DefaultTask {
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getModulesDirectory();

    @Input
    public abstract ListProperty<String> getIslands();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void registerModules() throws IOException {
        Path input = getModulesDirectory().get().getAsFile().toPath();
        Path output = getOutputDirectory().get().getAsFile().toPath();
        if (!Files.isDirectory(input)) {
            throw new GradleException("Kotlin/JS production modules are missing: " + input);
        }
        List<Path> modules;
        try (var paths = Files.walk(input)) {
            modules = paths.filter(Files::isRegularFile)
                    .filter(WogeKotlinJsIslandsTask::isJavaScript)
                    .sorted(Comparator.comparing(path -> input.relativize(path).toString()))
                    .toList();
        }
        if (modules.isEmpty()) {
            throw new GradleException("No Kotlin/JS production .js or .mjs modules found in " + input);
        }
        Map<String, Path> byName = new TreeMap<>();
        for (Path module : modules) {
            if (Files.isSymbolicLink(module)) {
                throw new GradleException("Symbolic links are not valid Kotlin/JS production modules: " + module);
            }
            byName.put(input.relativize(module).toString().replace('\\', '/'), module);
        }
        String version = treeHash(input, modules);
        String generatedSource = generatedSource(byName, version);
        deleteTree(output);
        Path tree = output.resolve("static/assets/woge/islands/" + version);
        for (Path module : modules) {
            Path relative = input.relativize(module);
            Path destination = tree.resolve(relative);
            Files.createDirectories(destination.getParent());
            Files.copy(module, destination);
        }
        Path source = output.resolve("java/dev/woge/generated/WogeIslandModules.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, generatedSource, StandardCharsets.UTF_8);
        writeReport(output.resolve("island-module-sizes.json"), modules, input);
    }

    private String generatedSource(Map<String, Path> modules, String version) {
        StringBuilder source = new StringBuilder("package dev.woge.generated;\n\n")
                .append("import dev.woge.html.IslandModule;\n")
                .append("import dev.woge.html.IslandType;\n\n")
                .append("public final class WogeIslandModules {\n")
                .append("    private WogeIslandModules() {}\n");
        for (String spec : getIslands().get()) {
            String[] parts = spec.split("\\|", -1);
            if (parts.length != 3 || !modules.containsKey(parts[1])) {
                throw new GradleException("Configured island module was not found in production output: " + spec);
            }
            if (!parts[0].matches("[A-Za-z][A-Za-z0-9_]{0,63}")
                    || !parts[2].matches("[A-Za-z_$][A-Za-z0-9_$]{0,63}")) {
                throw new GradleException("Invalid generated island name or mount export: " + spec);
            }
            String url = "/assets/woge/islands/" + version + "/" + parts[1];
            source.append("    public static IslandModule ").append(parts[0]).append("(IslandType<?> type) {\n")
                    .append("        return new IslandModule(type, \"").append(url).append("\", \"")
                    .append(version).append("\", \"").append(parts[2]).append("\");\n")
                    .append("    }\n");
        }
        return source.append("}\n").toString();
    }

    private void writeReport(Path report, List<Path> modules, Path input) throws IOException {
        StringBuilder json = new StringBuilder("{\n  \"modules\": [\n");
        for (int index = 0; index < modules.size(); index++) {
            Path module = modules.get(index);
            byte[] bytes = Files.readAllBytes(module);
            json.append("    {\"file\":\"").append(input.relativize(module).toString().replace('\\', '/'))
                    .append("\",\"raw\":").append(bytes.length)
                    .append(",\"gzip\":").append(gzipSize(bytes))
                    .append(",\"brotli\":").append(brotliSize(bytes)).append("}")
                    .append(index + 1 == modules.size() ? "\n" : ",\n");
        }
        Files.writeString(report, json.append("  ]\n}\n"), StandardCharsets.UTF_8);
    }

    private static int gzipSize(byte[] bytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(bytes);
        }
        return output.size();
    }

    private static int brotliSize(byte[] bytes) throws IOException {
        Process process;
        try {
            process = new ProcessBuilder("node", "-e", "const z=require('node:zlib');process.stdin.pipe(z.createBrotliCompress()).pipe(process.stdout)")
                    .start();
        } catch (IOException error) {
            throw new GradleException("Node.js is required to measure Brotli size for Kotlin/JS islands", error);
        }
        try (var stdin = process.getOutputStream()) {
            stdin.write(bytes);
        }
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (var stdout = process.getInputStream()) {
            stdout.transferTo(compressed);
        }
        try (var stderr = process.getErrorStream()) {
            stderr.readAllBytes();
        }
        try {
            int status = process.waitFor();
            if (status != 0) {
                throw new GradleException("Node.js Brotli compression failed with exit code " + status);
            }
            return compressed.size();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while measuring Brotli size", error);
        }
    }

    private static String treeHash(Path root, List<Path> modules) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Path module : modules) {
                digest.update(root.relativize(module).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Files.readAllBytes(module));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static boolean isJavaScript(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".js") || name.endsWith(".mjs");
    }

    private static void deleteTree(Path root) throws IOException {
        if (Files.exists(root)) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

}
