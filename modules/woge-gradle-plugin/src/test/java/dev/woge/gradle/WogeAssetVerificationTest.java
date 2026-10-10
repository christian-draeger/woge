package dev.woge.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WogeAssetVerificationTest {
    @TempDir Path root;

    @Test
    void validatesPlainAndBootJarsAgainstActualPackagedBytes() throws IOException {
        for (String prefix : List.of("", "BOOT-INF/classes/")) {
            String hash = WogeAssetsTask.bundleHash(Map.of("/site.css",
                    WogeAssetsTask.hash(new java.io.ByteArrayInputStream("body {}".getBytes(StandardCharsets.UTF_8)))));
            String manifest = manifest(hash);
            Map<String, String> entries = Map.of(prefix + WogeAssetsTask.MANIFEST, manifest,
                    prefix + WogeAssetsTask.RESOURCES + hash + "/site.css", "body {}");
            assertEquals(List.of(), verify(entries, manifest));
            assertFalse(verify(Map.of(prefix + WogeAssetsTask.MANIFEST, manifest), manifest).isEmpty());
            assertFalse(verify(Map.of(prefix + WogeAssetsTask.MANIFEST, manifest,
                    prefix + WogeAssetsTask.RESOURCES + hash + "/site.css", "changed"), manifest).isEmpty());
        }
    }

    @Test
    void rejectsStaleManifestsAndUnregisteredPackagedAssets() throws IOException {
        String hash = WogeAssetsTask.bundleHash(Map.of());
        String manifest = "schemaVersion=1\nbundleHash=" + hash + "\n";
        assertTrue(verify(Map.of(), manifest).get(0).contains("Missing"));
        assertTrue(verify(Map.of(WogeAssetsTask.MANIFEST, "old"), manifest).get(0).contains("differs"));
        assertTrue(verify(Map.of(WogeAssetsTask.MANIFEST, manifest,
                WogeAssetsTask.RESOURCES + hash + "/stale.css", "old"), manifest).get(0).contains("Unregistered"));
    }

    private List<String> verify(Map<String, String> entries, String expected) throws IOException {
        Path jar = root.resolve("assets.jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        List<String> problems = new ArrayList<>();
        try (ZipFile archive = new ZipFile(jar.toFile())) {
            WogeAssetVerification.verify(archive, expected, problems);
        }
        return problems;
    }

    private String manifest(String hash) {
        return "schemaVersion=1\nbundleHash=" + hash + "\nasset./site.css=/_woge/assets/" + hash + "/site.css\n";
    }
}
