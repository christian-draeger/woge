package dev.woge.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.gradle.api.GradleException;

final class ComponentInstaller {
    private static final String LOCK = "woge-components.lock.json";
    private final ComponentRegistry registry;
    private final Path root;
    private final Path reports;
    private final String wogeVersion;

    ComponentInstaller(ComponentRegistry registry, Path root, Path reports, String wogeVersion) {
        this.registry = registry;
        this.root = root;
        this.reports = reports;
        this.wogeVersion = wogeVersion;
    }

    List<String> add(String name, String variant, String packageName, boolean dryRun) throws IOException {
        ComponentRegistry.validatePackage(packageName);
        Map<String, Object> lock = lock(packageName);
        if (!lock.get("packageName").equals(packageName)) {
            throw new GradleException("Package differs from lockfile; keep " + lock.get("packageName"));
        }
        Map<String, Object> installed = ComponentJson.object(lock.get("components"));
        List<String> messages = new ArrayList<>();
        Map<String, String> writes = new TreeMap<>();
        for (String selected : registry.selection(name, variant, true)) {
            registry.checkCompatibility(selected, wogeVersion);
            if (installed.containsKey(selected)) {
                if (selected.equals(name)) throw new GradleException(selected + " is already installed; use wogeComponentUpdate");
                if (!ComponentJson.object(installed.get(selected)).get("variant").equals(variant)) {
                    throw new GradleException("Dependency variant differs: " + selected);
                }
                continue;
            }
            Map<String, String> files = registry.materialize(selected, variant, packageName);
            for (Map.Entry<String, String> file : files.entrySet()) {
                Path target = ComponentRegistry.contained(root, file.getKey());
                if (Files.exists(target) || writes.put(file.getKey(), file.getValue()) != null || owned(installed, file.getKey())) {
                    throw new GradleException("Refusing existing or shared target: " + file.getKey());
                }
            }
            installed.put(selected, entry(selected, variant, files));
            describe(selected, variant, files, messages);
        }
        if (!dryRun) {
            for (Map.Entry<String, String> file : writes.entrySet()) write(ComponentRegistry.contained(root, file.getKey()), file.getValue());
            lock.put("components", installed);
            write(ComponentRegistry.contained(root, LOCK), ComponentJson.write(lock));
        }
        messages.add(dryRun ? "Preview only; no files changed." : "Installed normal application sources; commit them and the lockfile.");
        return messages;
    }

    List<String> update(String name, boolean apply, boolean acceptLocal) throws IOException {
        if (acceptLocal && (!apply || name.isEmpty())) {
            throw new GradleException("--accept-local requires --apply and a single --name after reviewing the snapshots");
        }
        Map<String, Object> lock = lock(null);
        String packageName = ComponentJson.string(lock.get("packageName"));
        Map<String, Object> installed = ComponentJson.object(lock.get("components"));
        List<String> messages = new ArrayList<>();
        if (!name.isEmpty() && !installed.containsKey(name)) throw new GradleException("Not installed: " + name);
        boolean changed = false;
        for (String selected : new ArrayList<>(installed.keySet())) {
            if (!name.isEmpty() && !name.equals(selected)) continue;
            registry.checkCompatibility(selected, wogeVersion);
            Map<String, Object> old = ComponentJson.object(installed.get(selected));
            String variant = ComponentJson.string(old.get("variant"));
            for (String dependency : registry.selection(selected, variant, true)) {
                if (!dependency.equals(selected) && !installed.containsKey(dependency)) {
                    throw new GradleException("Install new dependency first: " + dependency + " --variant=" + variant);
                }
            }
            if (old.get("version").equals(registry.item(selected).get("version"))
                    && !old.get("metadata").equals(registry.item(selected))) {
                throw new GradleException("Immutable registry release changed without a version bump: " + selected);
            }
            Map<String, String> next = registry.materialize(selected, variant, packageName);
            Map<String, Object> base = ComponentJson.object(old.get("files"));
            Map<String, FileChange> changes = new TreeMap<>();
            boolean conflict = false;
            var paths = new java.util.TreeSet<>(base.keySet());
            paths.addAll(next.keySet());
            messages.add(selected + " " + old.get("version") + " -> " + registry.item(selected).get("version") + " (" + variant + ")");
            for (String path : paths) {
                if (!base.containsKey(path) && owned(installed, path)) throw new GradleException("Shared update target: " + path);
                Path target = ComponentRegistry.contained(root, path);
                String original = base.containsKey(path) ? ComponentJson.string(ComponentJson.object(base.get(path)).get("base")) : null;
                String local = Files.exists(target) ? Files.readString(target) : null;
                String upstream = next.get(path);
                boolean upstreamChanged = !Objects.equals(original, upstream);
                boolean localChanged = !Objects.equals(original, local);
                boolean hasConflict = upstreamChanged && localChanged && !Objects.equals(local, upstream);
                conflict |= hasConflict && !acceptLocal;
                String status = hasConflict ? acceptLocal ? "ACCEPT LOCAL (reviewed)" : "CONFLICT" : !upstreamChanged ? localChanged ? "LOCAL (preserved)" : "UNCHANGED"
                        : Objects.equals(local, upstream) ? "ALREADY MERGED" : upstream == null ? "REMOVE" : "UPDATE";
                messages.add("  " + status + " " + path);
                if (hasConflict) messages.add("    base=" + digest(original) + " local=" + digest(local) + " new=" + digest(upstream));
                changes.put(path, new FileChange(original, local, upstream, upstreamChanged && !localChanged, hasConflict));
            }
            if (conflict) {
                messages.add("  Item deferred. Review base/local/new snapshots; merge and use --apply --accept-local to acknowledge local edits.");
                if (apply) snapshots(selected, changes);
                continue;
            }
            if (apply) {
                for (Map.Entry<String, FileChange> file : changes.entrySet()) {
                    if (!file.getValue().replace()) continue;
                    Path target = ComponentRegistry.contained(root, file.getKey());
                    if (file.getValue().upstream() == null) Files.deleteIfExists(target);
                    else write(target, file.getValue().upstream());
                }
                installed.put(selected, entry(selected, variant, next));
                changed = true;
            }
        }
        if (changed) {
            lock.put("components", installed);
            write(ComponentRegistry.contained(root, LOCK), ComponentJson.write(lock));
        }
        messages.add(apply ? "Update applied where safe. Conflict snapshots: " + reports : "Check only; use --apply to write safe updates and conflict snapshots.");
        return messages;
    }

    private void snapshots(String name, Map<String, FileChange> changes) throws IOException {
        for (Map.Entry<String, FileChange> entry : changes.entrySet()) {
            FileChange change = entry.getValue();
            if (!change.conflict()) continue;
            String prefix = name + "/" + entry.getKey();
            snapshot(prefix + ".woge-base", change.base());
            snapshot(prefix + ".woge-local", change.local());
            snapshot(prefix + ".woge-new", change.upstream());
        }
    }

    private void snapshot(String path, String content) throws IOException {
        Path target = ComponentRegistry.contained(reports, path);
        if (content == null) Files.deleteIfExists(target);
        else write(target, content);
    }

    private void describe(String name, String variant, Map<String, String> files, List<String> messages) {
        Map<String, Object> item = registry.item(name);
        messages.add(name + " " + item.get("version") + " (" + variant + ") " + item.get("license"));
        messages.add("  Required modules (add to dependencies): " + ComponentJson.object(item.get("dependencies")).get("modules"));
        files.forEach((path, content) -> messages.add("  ADD " + path + " sha256=" + ComponentRegistry.hash(content)));
    }

    private Map<String, Object> entry(String name, String variant, Map<String, String> sources) {
        Map<String, Object> result = new TreeMap<>();
        result.put("version", registry.item(name).get("version"));
        result.put("variant", variant);
        result.put("metadata", registry.item(name));
        Map<String, Object> files = new TreeMap<>();
        sources.forEach((path, content) -> files.put(path, new TreeMap<>(Map.of("base", content, "sha256", ComponentRegistry.hash(content)))));
        result.put("files", files);
        return result;
    }

    private Map<String, Object> lock(String packageName) throws IOException {
        Path path = ComponentRegistry.contained(root, LOCK);
        if (!Files.exists(path)) {
            if (packageName == null) throw new GradleException("No component lockfile; install a component first");
            return new TreeMap<>(Map.of("schemaVersion", 1, "packageName", packageName, "components", new TreeMap<>()));
        }
        Map<String, Object> lock = ComponentJson.parse(Files.readString(path));
        if (!Integer.valueOf(1).equals(lock.get("schemaVersion"))) throw new GradleException("Unsupported component lockfile version");
        ComponentRegistry.validatePackage(ComponentJson.string(lock.get("packageName")));
        Map<String, Object> components = ComponentJson.object(lock.get("components"));
        var owners = new java.util.HashSet<String>();
        for (Map.Entry<String, Object> component : components.entrySet()) {
            if (!component.getKey().matches("[a-z][a-z0-9-]*")) throw new GradleException("Invalid locked component name");
            Map<String, Object> entry = ComponentJson.object(component.getValue());
            validateLockedEntry(component.getKey(), entry, lock.get("packageName").toString());
            for (Map.Entry<String, Object> file : ComponentJson.object(entry.get("files")).entrySet()) {
                ComponentRegistry.validateTarget(file.getKey());
                ComponentRegistry.contained(root, file.getKey());
                if (!owners.add(file.getKey())) throw new GradleException("Shared lockfile target: " + file.getKey());
                Map<String, Object> snapshot = ComponentJson.object(file.getValue());
                if (!ComponentRegistry.hash(ComponentJson.string(snapshot.get("base"))).equals(snapshot.get("sha256"))) {
                    throw new GradleException("Corrupt lockfile base hash: " + file.getKey());
                }
            }
        }
        return lock;
    }

    private static void validateLockedEntry(String name, Map<String, Object> entry, String packageName) throws IOException {
        Map<String, Object> metadata = ComponentJson.object(entry.get("metadata"));
        Map<String, Object> schema = ComponentJson.parse(ComponentRegistry.resource("schema.json"));
        Map<String, Object> itemArray = ComponentJson.object(ComponentJson.object(schema.get("properties")).get("items"));
        ComponentJson.validate(metadata, ComponentJson.object(itemArray.get("items")), "lock.metadata");
        if (!name.equals(metadata.get("name")) || !entry.get("version").equals(metadata.get("version"))) {
            throw new GradleException("Lockfile item metadata mismatch: " + name);
        }
        Map<String, Object> variants = ComponentJson.object(metadata.get("variants"));
        Map<String, Object> variant = ComponentJson.object(variants.get(ComponentJson.string(entry.get("variant"))));
        Map<String, Object> snapshots = ComponentJson.object(entry.get("files"));
        var expected = new java.util.TreeSet<String>();
        for (Object raw : ComponentJson.array(variant.get("files"))) {
            Map<String, Object> file = ComponentJson.object(raw);
            String target = file.get("target").toString().replace("__PACKAGE_PATH__", packageName.replace('.', '/'));
            expected.add(target);
            Map<String, Object> snapshot = ComponentJson.object(snapshots.get(target));
            String base = ComponentJson.string(snapshot.get("base"));
            String template = file.get("kind").equals("kotlin")
                    ? base.replaceFirst("(?m)^package " + java.util.regex.Pattern.quote(packageName) + "$", "package dev.woge.registry.template")
                    : base;
            if (!ComponentRegistry.hash(template).equals(file.get("sha256"))) {
                throw new GradleException("Lockfile base differs from declared source: " + target);
            }
        }
        if (!expected.equals(snapshots.keySet())) throw new GradleException("Lockfile targets differ from metadata: " + name);
    }

    private static boolean owned(Map<String, Object> installed, String path) {
        return installed.values().stream().anyMatch(value -> ComponentJson.object(ComponentJson.object(value).get("files")).containsKey(path));
    }

    private static String digest(String content) {
        return content == null ? "(absent)" : ComponentRegistry.hash(content);
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private record FileChange(String base, String local, String upstream, boolean replace, boolean conflict) {}
}
