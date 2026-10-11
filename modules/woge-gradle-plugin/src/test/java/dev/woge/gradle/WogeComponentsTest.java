package dev.woge.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.gradle.api.GradleException;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WogeComponentsTest {
    @TempDir Path root;
    private static final String PACKAGE = "example.components";
    private static final String KOTLIN = "src/main/kotlin/example/components/DisclosureCard.kt";
    private static final String CSS = "src/main/resources/static/assets/components/disclosure-card.css";

    @Test
    void registryValidatesEverySourceAndExplicitStyleVariant() throws IOException {
        ComponentRegistry registry = new ComponentRegistry(null);
        assertEquals(List.of("disclosure-card", "status-message"), new ArrayList<>(registry.items().keySet()));
        for (String name : registry.items().keySet()) {
            for (String variant : ComponentJson.object(registry.item(name).get("variants")).keySet()) {
                Map<String, String> sources = registry.materialize(name, variant, PACKAGE);
                assertTrue(sources.values().stream().anyMatch(source -> source.contains("package " + PACKAGE)));
                assertTrue(sources.keySet().stream().anyMatch(path -> path.endsWith(".txt")));
                assertFalse(sources.values().stream().anyMatch(source -> source.contains("__PACKAGE__")));
            }
        }
        assertThrows(GradleException.class, () -> registry.materialize("status-message", "tailwind", PACKAGE));
        assertThrows(GradleException.class, () -> registry.checkCompatibility("disclosure-card", "0.2.0"));
        assertThrows(GradleException.class, () -> registry.checkCompatibility("disclosure-card", "0.0.9"));
        registry.checkCompatibility("disclosure-card", "0.1.0-SNAPSHOT");
    }

    @Test
    void previewDoesNotWriteAndInstallIsDeterministicEditableSource() throws IOException {
        var installer = installer(null);
        installer.add("disclosure-card", "css", PACKAGE, true);
        assertFalse(Files.exists(root.resolve(KOTLIN)));
        assertFalse(Files.exists(root.resolve("woge-components.lock.json")));
        installer.add("disclosure-card", "css", PACKAGE, false);
        String lock = Files.readString(root.resolve("woge-components.lock.json"));
        assertTrue(lock.contains("Apache-2.0"));
        assertTrue(lock.contains("repository"));
        assertTrue(lock.contains("sha256"));
        String source = Files.readString(root.resolve(KOTLIN));
        assertTrue(source.contains("import dev.woge.ui.disclosure"));
        assertFalse(source.contains("registry"));
        assertTrue(Files.readString(root.resolve(CSS)).contains("@layer woge.components.disclosure-card"));
        assertThrows(GradleException.class, () -> installer.add("disclosure-card", "css", PACKAGE, false));
        installer.update("", true, false);
        assertEquals(lock, Files.readString(root.resolve("woge-components.lock.json")));
        assertEquals(source, Files.readString(root.resolve(KOTLIN)));
    }

    @Test
    void existingSourcesAreNotOverwrittenEvenWhenContentMatches() throws IOException {
        Files.createDirectories(root.resolve(CSS).getParent());
        Files.writeString(root.resolve(CSS), "/* application stylesheet */");
        assertThrows(GradleException.class, () -> installer(null).add("disclosure-card", "css", PACKAGE, false));
        assertEquals("/* application stylesheet */", Files.readString(root.resolve(CSS)));
        assertFalse(Files.exists(root.resolve(KOTLIN)));
        assertFalse(Files.exists(root.resolve("woge-components.lock.json")));
    }

    @Test
    void updatesCleanSourcesAndKeepsScopeAndLayerStable() throws IOException {
        installer(null).add("disclosure-card", "css", PACKAGE, false);
        Path mirror = mirror();
        reviseCss(mirror);
        var updater = installer(mirror);
        String before = Files.readString(root.resolve(CSS));
        assertTrue(updater.update("", false, false).stream().anyMatch(message -> message.contains("UPDATE " + CSS)));
        assertEquals(before, Files.readString(root.resolve(CSS)));
        updater.update("", true, false);
        String after = Files.readString(root.resolve(CSS));
        assertTrue(after.contains("padding: 2rem"));
        assertTrue(after.contains("@layer woge.components.disclosure-card"));
        assertTrue(after.contains("data-woge-scope=\"woge-disclosure-card\""));
        assertTrue(Files.readString(root.resolve("woge-components.lock.json")).contains("1.1.0"));
    }

    @Test
    void customizedSourcesRequireAnExplicitReviewedMerge() throws IOException {
        installer(null).add("disclosure-card", "css", PACKAGE, false);
        String original = Files.readString(root.resolve(CSS));
        String local = original + "\n/* app customization */\n";
        Files.writeString(root.resolve(CSS), local);
        String lock = Files.readString(root.resolve("woge-components.lock.json"));
        Path mirror = mirror();
        reviseCss(mirror);
        var updater = installer(mirror);
        assertTrue(updater.update("", false, false).stream().anyMatch(message -> message.contains("CONFLICT")));
        assertFalse(Files.exists(root.resolve("build/woge-components/update")));
        updater.update("", true, false);
        assertEquals(local, Files.readString(root.resolve(CSS)));
        assertEquals(lock, Files.readString(root.resolve("woge-components.lock.json")));
        Path snapshots = root.resolve("build/woge-components/update/disclosure-card/" + CSS);
        assertEquals(original, Files.readString(Path.of(snapshots + ".woge-base")));
        assertEquals(local, Files.readString(Path.of(snapshots + ".woge-local")));
        assertTrue(Files.readString(Path.of(snapshots + ".woge-new")).contains("padding: 2rem"));
        String merged = local.replace("padding: 1rem", "padding: 2rem");
        Files.writeString(root.resolve(CSS), merged);
        assertThrows(GradleException.class, () -> updater.update("", true, true));
        updater.update("disclosure-card", true, true);
        assertEquals(merged, Files.readString(root.resolve(CSS)));
        assertTrue(Files.readString(root.resolve("woge-components.lock.json")).contains("1.1.0"));
        assertFalse(updater.update("", false, false).stream().anyMatch(message -> message.contains("CONFLICT")));
    }

    @Test
    void unchangedUpstreamPreservesLocalEditsAndLocalDeletions() throws IOException {
        var installer = installer(null);
        installer.add("disclosure-card", "css", PACKAGE, false);
        Files.delete(root.resolve(KOTLIN));
        Files.writeString(root.resolve(CSS), "/* locally replaced */\n");
        var messages = installer.update("", true, false);
        assertTrue(messages.stream().anyMatch(message -> message.contains("LOCAL (preserved) " + KOTLIN)));
        assertFalse(Files.exists(root.resolve(KOTLIN)));
        assertEquals("/* locally replaced */\n", Files.readString(root.resolve(CSS)));
    }

    @Test
    void localDeletionConflictsWithChangedUpstream() throws IOException {
        installer(null).add("disclosure-card", "css", PACKAGE, false);
        Files.delete(root.resolve(CSS));
        Path mirror = mirror();
        reviseCss(mirror);
        var messages = installer(mirror).update("", true, false);
        assertTrue(messages.stream().anyMatch(message -> message.contains("CONFLICT " + CSS)));
        assertFalse(Files.exists(root.resolve(CSS)));
    }

    @Test
    void removedUpstreamStylesAreDeletedOnlyWhenLocallyUnchanged() throws IOException {
        installer(null).add("disclosure-card", "css", PACKAGE, false);
        Path mirror = mirror();
        removeCss(mirror);
        assertTrue(installer(mirror).update("", false, false).stream().anyMatch(message -> message.contains("REMOVE " + CSS)));
        assertTrue(Files.exists(root.resolve(CSS)));
        installer(mirror).update("", true, false);
        assertFalse(Files.exists(root.resolve(CSS)));
        assertTrue(Files.exists(root.resolve(KOTLIN)));
    }

    @Test
    void upstreamRemovalPreservesLocallyCustomizedStyles() throws IOException {
        installer(null).add("disclosure-card", "css", PACKAGE, false);
        Files.writeString(root.resolve(CSS), "/* custom CSS */");
        Path mirror = mirror();
        removeCss(mirror);
        assertTrue(installer(mirror).update("", true, false).stream().anyMatch(message -> message.contains("CONFLICT " + CSS)));
        assertEquals("/* custom CSS */", Files.readString(root.resolve(CSS)));
        assertFalse(Files.exists(root.resolve("build/woge-components/update/disclosure-card/" + CSS + ".woge-new")));
    }

    @Test
    void upstreamAdditionsWithLocalCollisionsDeferTheWholeItem() throws IOException {
        installer(null).add("disclosure-card", "css", PACKAGE, false);
        Path mirror = mirror();
        reviseCss(mirror);
        Map<String, Object> index = readIndex(mirror);
        List<Object> items = ComponentJson.array(index.get("items"));
        Map<String, Object> card = ComponentJson.object(items.get(0));
        Map<String, Object> variants = ComponentJson.object(card.get("variants"));
        Map<String, Object> variant = ComponentJson.object(variants.get("css"));
        List<Object> files = ComponentJson.array(variant.get("files"));
        String path = "src/main/resources/static/assets/components/help.txt";
        Files.writeString(mirror.resolve("help.txt"), "upstream addition");
        Files.createDirectories(root.resolve(path).getParent());
        Files.writeString(root.resolve(path), "application asset");
        files.add(Map.of("source", "help.txt", "target", path, "kind", "asset", "sha256", ComponentRegistry.hash("upstream addition")));
        variant.put("files", files);
        variants.put("css", variant);
        card.put("variants", variants);
        refreshProvenance(card);
        items.set(0, card);
        index.put("items", items);
        writeIndex(mirror, index);
        String originalCss = Files.readString(root.resolve(CSS));
        assertTrue(installer(mirror).update("", true, false).stream().anyMatch(message -> message.contains("CONFLICT " + path)));
        assertEquals("application asset", Files.readString(root.resolve(path)));
        assertEquals(originalCss, Files.readString(root.resolve(CSS)));
        Files.delete(root.resolve(path));
        installer(mirror).update("", true, false);
        assertEquals("upstream addition", Files.readString(root.resolve(path)));
        assertTrue(Files.readString(root.resolve(CSS)).contains("padding: 2rem"));
    }

    @Test
    void undeclaredStylesAndChangedProvenanceAreRejected() throws IOException {
        Path mirror = mirror();
        Map<String, Object> index = readIndex(mirror);
        List<Object> items = ComponentJson.array(index.get("items"));
        Map<String, Object> card = ComponentJson.object(items.get(0));
        Map<String, Object> variants = ComponentJson.object(card.get("variants"));
        Map<String, Object> css = ComponentJson.object(variants.get("css"));
        css.put("styleAssets", List.of());
        variants.put("css", css);
        card.put("variants", variants);
        items.set(0, card);
        index.put("items", items);
        writeIndex(mirror, index);
        assertThrows(GradleException.class, () -> new ComponentRegistry(mirror));
        mirror();
        index = readIndex(mirror);
        items = ComponentJson.array(index.get("items"));
        card = ComponentJson.object(items.get(0));
        Map<String, Object> provenance = ComponentJson.object(card.get("provenance"));
        provenance.put("revision", "0".repeat(64));
        card.put("provenance", provenance);
        items.set(0, card);
        index.put("items", items);
        writeIndex(mirror, index);
        assertThrows(GradleException.class, () -> new ComponentRegistry(mirror));
    }

    @Test
    void verifiesSchemaSourceHashesAndCompatibilityBeforeAnyWrite() throws IOException {
        Path mirror = mirror();
        Files.writeString(mirror.resolve("disclosure-card/css/DisclosureCard.kt"), "corrupt");
        Path corruptMirror = mirror;
        assertThrows(GradleException.class, () -> installer(corruptMirror));
        assertFalse(Files.exists(root.resolve("woge-components.lock.json")));
        mirror = mirror();
        Map<String, Object> index = readIndex(mirror);
        index.put("schemaVersion", 99);
        writeIndex(mirror, index);
        Path invalidMirror = mirror;
        assertThrows(GradleException.class, () -> installer(invalidMirror));
    }

    @Test
    void rejectsUnsafePathsPackagesAndSymlinks() throws IOException {
        ComponentRegistry registry = new ComponentRegistry(null);
        for (String path : List.of("../outside", "C:/outside", "a\\b", "/outside", "a/../b", "a//b", ".git/config")) {
            assertThrows(GradleException.class, () -> ComponentRegistry.validateTarget(path));
        }
        for (String name : List.of("example.class", "../outside", "com.MyPackage", "x;import")) {
            assertThrows(GradleException.class, () -> registry.materialize("disclosure-card", "css", name));
        }
        Path outside = root.resolve("outside");
        Files.createDirectories(outside);
        Files.createDirectories(root.resolve("src/main"));
        Files.createSymbolicLink(root.resolve("src/main/kotlin"), outside);
        assertThrows(GradleException.class, () -> installer(null).add("disclosure-card", "css", PACKAGE, false));
        try (var files = Files.list(outside)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void validatesItemDependenciesAndRejectsCycles() throws IOException {
        Path mirror = mirror();
        Map<String, Object> index = readIndex(mirror);
        List<Object> items = ComponentJson.array(index.get("items"));
        Map<String, Object> status = ComponentJson.object(items.get(1));
        Map<String, Object> dependencies = ComponentJson.object(status.get("dependencies"));
        dependencies.put("items", List.of("disclosure-card"));
        status.put("dependencies", dependencies);
        items.set(1, status);
        index.put("items", items);
        writeIndex(mirror, index);
        installer(mirror).add("status-message", "css", PACKAGE, false);
        assertTrue(Files.exists(root.resolve(KOTLIN)));
        assertTrue(Files.exists(root.resolve("src/main/kotlin/example/components/StatusMessage.kt")));
        Map<String, Object> card = ComponentJson.object(items.get(0));
        card.put("dependencies", Map.of("items", List.of("status-message"), "modules", List.of("dev.woge:woge-ui-headless")));
        items.set(0, card);
        index.put("items", items);
        writeIndex(mirror, index);
        assertThrows(GradleException.class, () -> new ComponentRegistry(mirror));
    }

    @Test
    void rejectsCorruptLockSnapshotsAndImmutableReleaseMutation() throws IOException {
        installer(null).add("disclosure-card", "css", PACKAGE, false);
        Path mirror = mirror();
        reviseCss(mirror);
        Map<String, Object> index = readIndex(mirror);
        List<Object> items = ComponentJson.array(index.get("items"));
        Map<String, Object> card = ComponentJson.object(items.get(0));
        card.put("version", "1.0.0");
        items.set(0, card);
        index.put("items", items);
        writeIndex(mirror, index);
        assertThrows(GradleException.class, () -> installer(mirror).update("", true, false));
        Path lock = root.resolve("woge-components.lock.json");
        Files.writeString(lock, Files.readString(lock).replace("package example.components", "package corrupt.components"));
        assertThrows(GradleException.class, () -> installer(null).update("", true, false));
    }

    @Test
    void tasksUseProvidersAndDoNotAttachInstallationToTheBuild() throws IOException {
        var project = ProjectBuilder.builder().withProjectDir(root.toFile()).build();
        project.getPluginManager().apply(WogeComponentsPlugin.class);
        var extension = project.getExtensions().getByType(WogeComponentsExtension.class);
        extension.getPackageName().set("app.ui");
        var task = (WogeComponentsTask) project.getTasks().getByName("wogeComponentAdd");
        assertEquals("app.ui", task.getPackageName().get());
        assertTrue(task.getTaskDependencies().getDependencies(task).isEmpty());
        task.selectComponentName("disclosure-card");
        task.selectPreview(true);
        task.components();
        assertFalse(Files.exists(root.resolve("woge-components.lock.json")));
    }

    @Test
    void cliOptionsAndConfigurationCacheWorkWithoutNodeOrAHostPlugin() throws IOException {
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'component-consumer'\n");
        Files.writeString(root.resolve("build.gradle"), "plugins { id 'dev.woge.components' }\n");
        var preview = runner("wogeComponentAdd", "--name=disclosure-card", "--package=app.ui", "--preview").build();
        assertTrue(preview.getOutput().contains("Preview only"), preview.getOutput());
        assertFalse(Files.exists(root.resolve("woge-components.lock.json")));
        var reused = runner("wogeComponentAdd", "--name=disclosure-card", "--package=app.ui", "--preview").build();
        assertTrue(reused.getOutput().contains("Reusing configuration cache"));
        runner("wogeComponentAdd", "--name=disclosure-card", "--variant=tailwind", "--package=app.ui").build();
        assertTrue(Files.exists(root.resolve("src/main/kotlin/app/ui/DisclosureCard.kt")));
        assertFalse(Files.exists(root.resolve(CSS)));
        var check = runner("wogeComponentUpdate").build();
        assertTrue(check.getOutput().contains("UNCHANGED"));
    }

    private GradleRunner runner(String... arguments) {
        List<String> args = new ArrayList<>(List.of(arguments));
        args.addAll(List.of("--configuration-cache", "--console=plain", "--max-workers=2"));
        return GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath().withArguments(args);
    }

    private ComponentInstaller installer(Path mirror) throws IOException {
        return new ComponentInstaller(new ComponentRegistry(mirror), root, root.resolve("build/woge-components/update"), "0.1.0");
    }

    private Path mirror() throws IOException {
        Path mirror = root.resolve("mirror");
        Files.createDirectories(mirror);
        String text = ComponentRegistry.resource("index.json");
        for (Object raw : ComponentJson.array(ComponentJson.parse(text).get("items"))) {
            for (Object variant : ComponentJson.object(ComponentJson.object(raw).get("variants")).values()) {
                Map<String, Object> data = ComponentJson.object(variant);
                List<Object> files = new ArrayList<>(ComponentJson.array(data.get("files")));
                files.addAll(ComponentJson.array(data.get("tests")));
                for (Object file : files) {
                    String source = ComponentJson.string(ComponentJson.object(file).get("source"));
                    Path target = mirror.resolve(source);
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, ComponentRegistry.resource(source));
                }
            }
        }
        Files.writeString(mirror.resolve("index.json"), text);
        return mirror;
    }

    private void removeCss(Path mirror) throws IOException {
        Map<String, Object> index = readIndex(mirror);
        List<Object> items = ComponentJson.array(index.get("items"));
        Map<String, Object> card = ComponentJson.object(items.get(0));
        card.put("version", "1.1.0");
        Map<String, Object> variants = ComponentJson.object(card.get("variants"));
        Map<String, Object> css = ComponentJson.object(variants.get("css"));
        List<Object> files = ComponentJson.array(css.get("files"));
        files.removeIf(file -> ComponentJson.object(file).get("kind").equals("css"));
        css.put("files", files);
        css.put("styleAssets", List.of());
        variants.put("css", css);
        card.put("variants", variants);
        refreshProvenance(card);
        items.set(0, card);
        index.put("items", items);
        writeIndex(mirror, index);
    }

    private void reviseCss(Path mirror) throws IOException {
        Map<String, Object> index = readIndex(mirror);
        List<Object> items = ComponentJson.array(index.get("items"));
        Map<String, Object> card = ComponentJson.object(items.get(0));
        card.put("version", "1.1.0");
        Map<String, Object> variants = ComponentJson.object(card.get("variants"));
        Map<String, Object> css = ComponentJson.object(variants.get("css"));
        List<Object> files = ComponentJson.array(css.get("files"));
        for (int i = 0; i < files.size(); i++) {
            Map<String, Object> file = ComponentJson.object(files.get(i));
            if (!file.get("kind").equals("css")) continue;
            Path source = mirror.resolve(ComponentJson.string(file.get("source")));
            String content = Files.readString(source).replace("padding: 1rem", "padding: 2rem");
            Files.writeString(source, content);
            file.put("sha256", ComponentRegistry.hash(content));
            files.set(i, file);
        }
        css.put("files", files);
        variants.put("css", css);
        card.put("variants", variants);
        refreshProvenance(card);
        items.set(0, card);
        index.put("items", items);
        writeIndex(mirror, index);
    }

    private static void refreshProvenance(Map<String, Object> item) {
        Map<String, String> hashes = new TreeMap<>();
        for (Object raw : ComponentJson.object(item.get("variants")).values()) {
            Map<String, Object> variant = ComponentJson.object(raw);
            List<Object> files = new ArrayList<>(ComponentJson.array(variant.get("files")));
            files.addAll(ComponentJson.array(variant.get("tests")));
            for (Object source : files) {
                Map<String, Object> file = ComponentJson.object(source);
                hashes.put(file.get("source").toString(), file.get("sha256").toString());
            }
        }
        Map<String, Object> provenance = ComponentJson.object(item.get("provenance"));
        provenance.put("revision", ComponentRegistry.sourceTreeHash(hashes));
        item.put("provenance", provenance);
    }

    private static Map<String, Object> readIndex(Path mirror) throws IOException {
        return ComponentJson.parse(Files.readString(mirror.resolve("index.json")));
    }

    private static void writeIndex(Path mirror, Map<String, Object> index) throws IOException {
        Files.writeString(mirror.resolve("index.json"), ComponentJson.write(new TreeMap<>(index)));
    }
}
