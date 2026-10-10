package dev.woge.gradle;

import groovy.json.JsonOutput;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

/** Composes only compiler-emitted catalogues and explicitly selected build settings. */
@CacheableTask
public abstract class WogeManifestTask extends DefaultTask {
    static final String CATALOGUE_PREFIX = "META-INF/woge/descriptors/";
    private static final Set<String> FIELDS =
            Set.of("kind", "id", "declaration", "inputType", "path", "component", "keyType");
    private static final Set<String> KINDS = Set.of("page", "action", "component", "region");

    @Classpath
    public abstract ConfigurableFileCollection getDescriptorClasspath();

    @Input
    public abstract Property<String> getWogeVersion();

    @Input
    @Optional
    public abstract Property<String> getKotlinVersion();

    @Input
    @Optional
    public abstract Property<String> getHostAdapter();

    @Input
    public abstract ListProperty<String> getCapabilities();

    @Input
    public abstract Property<String> getFrontendMode();

    @OutputFile
    public abstract RegularFileProperty getManifestFile();

    @TaskAction
    public void generate() {
        Path output = getManifestFile().get().getAsFile().toPath();
        // A failed build must not leave a success-shaped catalogue from the previous build.
        try {
            Files.deleteIfExists(output);
            Map<String, Map<String, String>> descriptors = new TreeMap<>();
            for (File entry : getDescriptorClasspath()) {
                readEntry(entry, descriptors);
            }
            Map<String, Object> manifest = new TreeMap<>();
            manifest.put("schemaVersion", 1);
            manifest.put("wogeVersion", required(getWogeVersion().get(), "wogeVersion"));
            manifest.put("kotlinVersion", required(getKotlinVersion().getOrNull(), "kotlinVersion"));
            String host = required(getHostAdapter().getOrNull(), "hostAdapter");
            if (!Set.of("spring-mvc", "spring-webflux", "ktor").contains(host)) {
                throw new GradleException("wogeManifest.hostAdapter must be spring-mvc, spring-webflux or ktor.");
            }
            manifest.put("hostAdapter", host);
            manifest.put("capabilities", getCapabilities().get().stream()
                    .map(value -> required(value, "capability")).distinct().sorted().toList());
            manifest.put("frontendMode", required(getFrontendMode().get(), "frontendMode"));
            manifest.put("documentation", new TreeMap<>(Map.of(
                    "home", "https://github.com/christian-draeger/woge",
                    "guides", "https://github.com/christian-draeger/woge/tree/main/docs/guides",
                    "manifest", "https://github.com/christian-draeger/woge/blob/main/docs/guides/application-manifest.md")));
            manifest.put("descriptors", new ArrayList<>(descriptors.values()));
            Files.createDirectories(output.getParent());
            Files.writeString(output, JsonOutput.prettyPrint(JsonOutput.toJson(manifest)) + "\n", StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isISOControl)) {
            throw new GradleException("wogeManifest." + name + " must be a non-empty structural value.");
        }
        return value;
    }

    private static void readEntry(File entry, Map<String, Map<String, String>> descriptors) throws IOException {
        if (entry.isDirectory()) {
            Path root = entry.toPath().resolve(CATALOGUE_PREFIX);
            if (Files.isDirectory(root)) {
                try (var files = Files.walk(root)) {
                    for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                        if (file.toString().endsWith(".properties")) {
                            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                                readCatalogue(reader, file.toString(), descriptors);
                            }
                        }
                    }
                }
            }
        } else if (entry.isFile() && entry.getName().endsWith(".jar")) {
            try (ZipFile jar = new ZipFile(entry)) {
                for (ZipEntry file : jar.stream().filter(candidate ->
                        candidate.getName().startsWith(CATALOGUE_PREFIX)
                                && candidate.getName().endsWith(".properties")).toList()) {
                    try (var input = jar.getInputStream(file)) {
                        readCatalogue(new StringReader(new String(input.readAllBytes(), StandardCharsets.UTF_8)),
                                entry.getName() + "!/" + file.getName(), descriptors);
                    }
                }
            }
        }
    }

    private static void readCatalogue(Reader reader, String origin,
            Map<String, Map<String, String>> descriptors) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);
        if (!"1".equals(properties.remove("schemaVersion"))) {
            throw new GradleException("Unsupported Woge descriptor catalogue schema in " + origin);
        }
        Map<String, Map<String, String>> entries = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            int separator = key.indexOf('.');
            String field = key.substring(separator + 1);
            if (separator < 1 || !key.substring(0, separator).matches("[0-9]+") || !FIELDS.contains(field)) {
                throw new GradleException("Unsupported Woge descriptor field in " + origin + ": " + key);
            }
            entries.computeIfAbsent(key.substring(0, separator), ignored -> new TreeMap<>())
                    .put(field, properties.getProperty(key));
        }
        for (Map<String, String> descriptor : entries.values()) {
            String kind = descriptor.getOrDefault("kind", "").toLowerCase(java.util.Locale.ROOT);
            if (!KINDS.contains(kind) || !descriptor.containsKey("id") || !descriptor.containsKey("declaration")) {
                throw new GradleException("Incomplete Woge descriptor in " + origin);
            }
            descriptor.put("kind", kind);
            descriptor.forEach((field, value) -> required(value, "descriptor." + field));
            String identity = kind + ":" + required(descriptor.get("id"), "descriptor.id");
            if (descriptors.putIfAbsent(identity, descriptor) != null) {
                throw new GradleException("Duplicate Woge descriptor " + identity + " in " + origin);
            }
        }
    }
}
