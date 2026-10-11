package dev.woge.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.gradle.api.GradleException;

final class ComponentRegistry {
    private static final String ROOT = "/dev/woge/gradle/registry/";
    private final Path directory;
    private final Map<String, Map<String, Object>> items = new TreeMap<>();

    ComponentRegistry(Path directory) throws IOException {
        this.directory = directory;
        Map<String, Object> index = ComponentJson.parse(read("index.json"));
        ComponentJson.validate(index, ComponentJson.parse(resource("schema.json")), "index");
        for (Object value : ComponentJson.array(index.get("items"))) {
            Map<String, Object> item = ComponentJson.object(value);
            String name = ComponentJson.string(item.get("name"));
            if (items.put(name, item) != null) throw new GradleException("Duplicate registry item: " + name);
            validateItem(item);
        }
        for (String name : items.keySet()) selection(name, "css", false);
    }

    Map<String, Map<String, Object>> items() { return items; }

    Map<String, Object> item(String name) {
        Map<String, Object> item = items.get(name);
        if (item == null) throw new GradleException("Unknown component: " + name);
        return item;
    }

    Map<String, String> materialize(String name, String variant, String packageName) throws IOException {
        validatePackage(packageName);
        Map<String, Object> variants = ComponentJson.object(item(name).get("variants"));
        if (!variants.containsKey(variant)) throw new GradleException(name + " has no " + variant + " variant");
        Map<String, String> files = new TreeMap<>();
        for (Object value : ComponentJson.array(ComponentJson.object(variants.get(variant)).get("files"))) {
            Map<String, Object> file = ComponentJson.object(value);
            String target = ComponentJson.string(file.get("target"))
                    .replace("__PACKAGE_PATH__", packageName.replace('.', '/'));
            safePath(target);
            String content = read(ComponentJson.string(file.get("source")));
            if (file.get("kind").equals("kotlin")) {
                content = content.replaceFirst("(?m)^package dev\\.woge\\.registry\\.template$", "package " + packageName);
            }
            if (files.put(target, content) != null) throw new GradleException("Duplicate component target: " + target);
        }
        return files;
    }

    List<String> selection(String name, String variant, boolean checkVariant) {
        Set<String> selected = new LinkedHashSet<>();
        visit(name, variant, checkVariant, selected, new LinkedHashSet<>());
        return new ArrayList<>(selected);
    }

    private void visit(String name, String variant, boolean checkVariant, Set<String> selected, Set<String> visiting) {
        if (selected.contains(name)) return;
        if (!visiting.add(name)) throw new GradleException("Cyclic component dependency: " + name);
        Map<String, Object> item = item(name);
        if (checkVariant && !ComponentJson.object(item.get("variants")).containsKey(variant)) {
            throw new GradleException(name + " has no " + variant + " variant");
        }
        for (Object dependency : ComponentJson.array(ComponentJson.object(item.get("dependencies")).get("items"))) {
            visit(ComponentJson.string(dependency), variant, checkVariant, selected, visiting);
        }
        visiting.remove(name);
        selected.add(name);
    }

    private void validateItem(Map<String, Object> item) throws IOException {
        String name = ComponentJson.string(item.get("name"));
        Map<String, Object> compatibility = ComponentJson.object(item.get("compatibility"));
        Map<String, Object> range = ComponentJson.object(compatibility.get("woge"));
        if (compareVersions(range.get("minimum").toString(), range.get("maximumExclusive").toString()) >= 0) {
            throw new GradleException("Invalid Woge compatibility range: " + name);
        }
        Map<String, String> sourceHashes = new TreeMap<>();
        for (Object value : ComponentJson.object(item.get("variants")).values()) {
            Map<String, Object> variant = ComponentJson.object(value);
            Map<String, String> kinds = new TreeMap<>();
            for (Object raw : ComponentJson.array(variant.get("files"))) {
                Map<String, Object> file = ComponentJson.object(raw);
                String source = ComponentJson.string(file.get("source"));
                safePath(source);
                validateTarget(ComponentJson.string(file.get("target")).replace("__PACKAGE_PATH__", "example/components"));
                if (kinds.put(source, ComponentJson.string(file.get("kind"))) != null) {
                    throw new GradleException("Duplicate registry source: " + source);
                }
                validateFile(file);
                sourceHashes.put(source, ComponentJson.string(file.get("sha256")));
                if (!hash(read(source)).equals(file.get("sha256"))) {
                    throw new GradleException("Registry content hash mismatch: " + source);
                }
            }
            if (!kinds.containsValue("kotlin") || !kinds.containsValue("license")) {
                throw new GradleException("Component must contain Kotlin and license sources: " + name);
            }
            for (Object raw : ComponentJson.array(variant.get("tests"))) {
                Map<String, Object> test = ComponentJson.object(raw);
                String source = ComponentJson.string(test.get("source"));
                safePath(source);
                sourceHashes.put(source, ComponentJson.string(test.get("sha256")));
                if (!source.endsWith(".kt") || !hash(read(source)).equals(test.get("sha256"))) {
                    throw new GradleException("Invalid registry test source: " + source);
                }
            }
            Set<String> declaredStyles = new LinkedHashSet<>();
            for (Object raw : ComponentJson.array(variant.get("styleAssets"))) {
                Map<String, Object> asset = ComponentJson.object(raw);
                String source = ComponentJson.string(asset.get("file"));
                String kind = ComponentJson.string(asset.get("kind"));
                if (!kinds.containsKey(source) || !declaredStyles.add(source)) {
                    throw new GradleException("Missing or duplicate style source: " + source);
                }
                if (kind.equals("tailwind") ? !kinds.get(source).equals("kotlin") : !kinds.get(source).equals("css")) {
                    throw new GradleException("Invalid style source kind: " + source);
                }
                if (kind.equals("scoped") && (!asset.getOrDefault("scope", "").equals("woge-" + name)
                        || !asset.get("layer").equals("woge.components." + name))) {
                    throw new GradleException("Scoped style identity must derive from the item name: " + name);
                }
            }
            for (Map.Entry<String, String> source : kinds.entrySet()) {
                if (source.getValue().equals("css") && !declaredStyles.contains(source.getKey())) {
                    throw new GradleException("Undeclared stylesheet: " + source.getKey());
                }
            }
        }
        Map<String, Object> provenance = ComponentJson.object(item.get("provenance"));
        if (provenance.get("revisionKind").equals("source-tree-sha256")
                && !sourceTreeHash(sourceHashes).equals(provenance.get("revision"))) {
            throw new GradleException("Registry source-tree provenance hash mismatch: " + name);
        }
    }

    private void validateFile(Map<String, Object> file) throws IOException {
        String kind = ComponentJson.string(file.get("kind"));
        String target = ComponentJson.string(file.get("target"));
        String source = ComponentJson.string(file.get("source"));
        boolean valid = switch (kind) {
            case "kotlin" -> target.startsWith("src/main/kotlin/__PACKAGE_PATH__/")
                    && target.endsWith(".kt") && source.endsWith(".kt")
                    && java.util.regex.Pattern.compile("(?m)^package dev\\.woge\\.registry\\.template$").matcher(read(source)).find();
            case "css" -> target.startsWith("src/main/resources/static/") && target.endsWith(".css") && source.endsWith(".css");
            case "license" -> target.startsWith("woge-components/licenses/");
            default -> target.startsWith("src/main/resources/static/");
        };
        if (!valid) throw new GradleException("Source kind, target or template package mismatch: " + source);
    }

    static String sourceTreeHash(Map<String, String> hashes) {
        StringBuilder tree = new StringBuilder();
        new TreeMap<>(hashes).forEach((path, digest) -> tree.append(path).append(' ').append(digest).append('\n'));
        return hash(tree.toString());
    }

    void checkCompatibility(String name, String version) {
        Map<String, Object> range = ComponentJson.object(ComponentJson.object(item(name).get("compatibility")).get("woge"));
        String stable = version.split("-", 2)[0];
        if (compareVersions(stable, range.get("minimum").toString()) < 0
                || compareVersions(stable, range.get("maximumExclusive").toString()) >= 0) {
            throw new GradleException(name + " is not compatible with Woge " + version + "; requires " + range);
        }
    }

    private static int compareVersions(String left, String right) {
        if (!left.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new GradleException("Invalid Woge version: " + left);
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < 3; i++) {
            int result = new java.math.BigInteger(a[i]).compareTo(new java.math.BigInteger(b[i]));
            if (result != 0) return result;
        }
        return 0;
    }

    private String read(String path) throws IOException {
        if (directory == null) return resource(path);
        return Files.readString(contained(directory, path), StandardCharsets.UTF_8);
    }

    static String resource(String path) throws IOException {
        try (InputStream input = ComponentRegistry.class.getResourceAsStream(ROOT + path)) {
            if (input == null) throw new GradleException("Missing registry resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void validatePackage(String name) {
        if (!name.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*")) {
            throw new GradleException("Use a lower-case Kotlin package name: " + name);
        }
        Set<String> keywords = Set.of("as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
                "in", "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true",
                "try", "typealias", "typeof", "val", "var", "when", "while");
        for (String part : name.split("\\.")) {
            if (keywords.contains(part)) throw new GradleException("Kotlin keyword in package: " + name);
        }
    }

    static void validateTarget(String path) {
        safePath(path);
        if (!(path.startsWith("src/main/kotlin/") && path.endsWith(".kt")
                || path.startsWith("src/main/resources/static/")
                || path.startsWith("woge-components/licenses/"))) {
            throw new GradleException("Target must be normal Kotlin, static assets or component licenses: " + path);
        }
    }

    static void safePath(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || path.contains(":")
                || path.contains("\n") || path.contains("\r") || path.indexOf('\0') >= 0) {
            throw new GradleException("Unsafe registry path: " + path);
        }
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new GradleException("Unsafe registry path: " + path);
            }
        }
    }

    static Path contained(Path root, String relative) throws IOException {
        safePath(relative);
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(relative);
        for (Path cursor = target; cursor != null && cursor.startsWith(normalizedRoot); cursor = cursor.getParent()) {
            if (Files.isSymbolicLink(cursor)) throw new GradleException("Refusing symbolic link: " + cursor);
        }
        return target;
    }

    static String hash(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
