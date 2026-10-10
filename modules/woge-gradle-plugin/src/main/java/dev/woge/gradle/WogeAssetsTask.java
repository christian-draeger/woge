package dev.woge.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Packages one content-addressed asset tree without rewriting CSS or requiring a JavaScript toolchain. */
@CacheableTask
public abstract class WogeAssetsTask extends DefaultTask {
    static final String MANIFEST = "META-INF/woge/assets.properties";
    static final String RESOURCES = "META-INF/woge/assets/";
    static final String URL_PREFIX = "/_woge/assets/";

    @Internal
    public abstract DirectoryProperty getSourceDirectory();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceFiles();

    @Input
    public java.util.List<String> getLogicalPaths() {
        Path root = getSourceDirectory().get().getAsFile().toPath().toAbsolutePath().normalize();
        return getSourceFiles().getFiles().stream()
                .map(file -> encodePath(root.relativize(file.toPath().toAbsolutePath().normalize())))
                .sorted().toList();
    }

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @TaskAction
    public void generate() {
        Path source = getSourceDirectory().get().getAsFile().toPath().toAbsolutePath().normalize();
        Path output = getOutputDirectory().get().getAsFile().toPath().toAbsolutePath().normalize();
        if (!output.endsWith(Path.of("woge-assets", "resources"))) {
            throw new GradleException("Woge asset output must use a dedicated woge-assets/resources directory.");
        }
        if (output.startsWith(source) || source.startsWith(output)) {
            throw new GradleException("Woge asset input and output directories must not overlap.");
        }
        getFileSystemOperations().delete(spec -> spec.delete(output));
        try {
            Map<String, Path> files = new TreeMap<>();
            Map<String, byte[]> hashes = new TreeMap<>();
            for (var file : getSourceFiles()) {
                Path path = file.toPath().toAbsolutePath().normalize();
                requireOwnedFile(source, path);
                String logical = encodePath(source.relativize(path));
                if (logical.startsWith(URL_PREFIX)) {
                    throw new GradleException("Static assets must not use Woge's reserved /_woge/assets/ namespace.");
                }
                files.put(logical, path);
                hashes.put(logical, hash(path));
            }
            String bundle = bundleHash(hashes);
            StringBuilder manifest = new StringBuilder("schemaVersion=1\nbundleHash=" + bundle + "\n");
            for (var asset : files.entrySet()) {
                Path relative = source.relativize(asset.getValue());
                Path target = output.resolve(RESOURCES + bundle).resolve(relative);
                Files.createDirectories(target.getParent());
                Files.copy(asset.getValue(), target, StandardCopyOption.REPLACE_EXISTING);
                if (!java.util.Arrays.equals(hash(target), hashes.get(asset.getKey()))) {
                    throw new GradleException("Static assets changed during packaging; rerun the build.");
                }
                manifest.append("asset.").append(asset.getKey()).append('=')
                        .append(URL_PREFIX).append(bundle).append(asset.getKey()).append('\n');
            }
            Path target = output.resolve(MANIFEST);
            Files.createDirectories(target.getParent());
            Files.writeString(target, manifest, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            getFileSystemOperations().delete(spec -> spec.delete(output));
            throw new UncheckedIOException(failure);
        } catch (RuntimeException failure) {
            getFileSystemOperations().delete(spec -> spec.delete(output));
            throw failure;
        }
    }

    static String bundleHash(Map<String, byte[]> hashes) {
        MessageDigest digest = sha256();
        new TreeMap<>(hashes).forEach((logical, content) -> {
            byte[] path = logical.getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(path.length).array());
            digest.update(path);
            digest.update(content);
        });
        return HexFormat.of().formatHex(digest.digest());
    }

    static byte[] hash(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            return hash(input);
        }
    }

    static byte[] hash(java.io.InputStream input) throws IOException {
        MessageDigest digest = sha256();
        byte[] buffer = new byte[8192];
        for (int count = input.read(buffer); count != -1; count = input.read(buffer)) {
            digest.update(buffer, 0, count);
        }
        return digest.digest();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("The JVM must support SHA-256.", failure);
        }
    }

    private static String encodePath(Path relative) {
        StringBuilder result = new StringBuilder();
        for (Path segment : relative) {
            result.append('/').append(URLEncoder.encode(segment.toString(), StandardCharsets.UTF_8)
                    .replace("+", "%20").replace("*", "%2A"));
        }
        return result.toString();
    }

    private static void requireOwnedFile(Path root, Path path) {
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new GradleException("Woge assets must be regular files under the configured static directory.");
        }
        for (Path candidate = path; candidate != null && candidate.startsWith(root); candidate = candidate.getParent()) {
            if (Files.isSymbolicLink(candidate)) {
                throw new GradleException("Woge static assets must not include symbolic links.");
            }
        }
    }
}
