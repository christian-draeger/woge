package dev.woge.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import groovy.json.JsonSlurper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.GradleException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WogeManifestTaskTest {
    @TempDir Path root;

    @Test
    void composesDirectoryAndLibraryCataloguesDeterministically() throws IOException {
        Path directory = catalogue("app", "pages", "schemaVersion=1\n"
                + "0.kind=PAGE\n0.id=/shop/{id}\n0.declaration=shop.ShopRoute\n0.path=/shop/{id}\n");
        Path library = root.resolve("library.jar");
        try (ZipOutputStream jar = new ZipOutputStream(Files.newOutputStream(library))) {
            jar.putNextEntry(new ZipEntry(WogeManifestTask.CATALOGUE_PREFIX + "library.properties"));
            jar.write(("schemaVersion=1\n0.kind=ACTION\n0.id=save\n0.declaration=library.SaveAction\n"
                    + "0.inputType=library.Command\n").getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        WogeManifestTask task = task();
        task.getDescriptorClasspath().from(directory, library);
        task.generate();
        String original = Files.readString(output());
        task.getDescriptorClasspath().setFrom(library, directory);
        task.generate();
        assertEquals(original, Files.readString(output()));
        assertTrue(original.indexOf("\"save\"") < original.indexOf("\"/shop/{id}\""));
        Object parsed = new JsonSlurper().parseText(original);
        assertTrue(parsed instanceof Map<?, ?>);
        Map<?, ?> manifest = (Map<?, ?>) parsed;
        assertEquals(1, manifest.get("schemaVersion"));
        assertEquals("ktor", manifest.get("hostAdapter"));
        assertEquals(List.of("actions", "pages"), manifest.get("capabilities"));
        assertEquals(2, ((List<?>) manifest.get("descriptors")).size());
        assertEquals(java.util.Set.of("schemaVersion", "wogeVersion", "kotlinVersion", "hostAdapter",
                "capabilities", "frontendMode", "documentation", "descriptors"), manifest.keySet());
    }

    @Test
    void removedCataloguesRemoveStaleDescriptors() throws IOException {
        Path directory = catalogue("app", "page", "schemaVersion=1\n"
                + "0.kind=PAGE\n0.id=/removed\n0.declaration=shop.RemovedRoute\n");
        WogeManifestTask task = task();
        task.getDescriptorClasspath().from(directory);
        task.generate();
        Files.delete(directory.resolve(WogeManifestTask.CATALOGUE_PREFIX + "page.properties"));
        task.generate();
        assertFalse(Files.readString(output()).contains("RemovedRoute"));
    }

    @Test
    void conflictingLibraryIdentityFailsAndRemovesPreviousManifest() throws IOException {
        String content = "schemaVersion=1\n0.kind=ACTION\n0.id=save\n0.declaration=shop.SaveAction\n";
        WogeManifestTask task = task();
        task.getDescriptorClasspath().from(catalogue("one", "one", content));
        task.generate();
        task.getDescriptorClasspath().from(catalogue("two", "two", content));
        assertThrows(GradleException.class, task::generate);
        assertFalse(Files.exists(output()));
    }

    @Test
    void unsupportedSchemaAndUnapprovedFieldsFailClosed() throws IOException {
        for (String content : List.of("schemaVersion=2\n",
                "schemaVersion=1\n0.kind=PAGE\n0.id=/\n0.declaration=HomeRoute\n0.html=secret\n",
                "schemaVersion=1\n0.kind=UNKNOWN\n0.id=/\n0.declaration=HomeRoute\n")) {
            WogeManifestTask task = task();
            task.getDescriptorClasspath().from(catalogue("app", "page", content));
            assertThrows(GradleException.class, task::generate);
            assertFalse(Files.exists(output()));
        }
    }

    @Test
    void invalidHostDoesNotLeaveStaleManifest() {
        WogeManifestTask task = task();
        task.generate();
        task.getHostAdapter().set("unknown");
        assertThrows(GradleException.class, task::generate);
        assertFalse(Files.exists(output()));
    }

    @Test
    void missingBuildConfigurationFailsExplicitlyAndRemovesPreviousManifest() {
        WogeManifestTask task = task();
        task.generate();
        task.getKotlinVersion().unset();
        assertThrows(GradleException.class, task::generate);
        assertFalse(Files.exists(output()));
    }

    private Path catalogue(String directory, String name, String content) throws IOException {
        Path target = root.resolve(directory).resolve(WogeManifestTask.CATALOGUE_PREFIX + name + ".properties");
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
        return root.resolve(directory);
    }

    private Path output() {
        return root.resolve(".woge/manifest.json");
    }

    private WogeManifestTask task() {
        var project = ProjectBuilder.builder().withProjectDir(root.toFile()).build();
        WogeManifestTask task = project.getTasks().register("manifest", WogeManifestTask.class).get();
        task.getManifestFile().set(output().toFile());
        task.getWogeVersion().set("0.1.0");
        task.getKotlinVersion().set("2.4.10");
        task.getHostAdapter().set("ktor");
        task.getFrontendMode().set("server-html");
        task.getCapabilities().set(List.of("pages", "actions", "pages"));
        return task;
    }
}
