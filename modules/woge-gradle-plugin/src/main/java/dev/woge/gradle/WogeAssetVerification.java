package dev.woge.gradle;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Verifies the actual packaged tree, not just the generator's output directory. */
final class WogeAssetVerification {
    private WogeAssetVerification() {}

    static void verify(ZipFile archive, String expected, List<String> problems) throws IOException {
        String prefix = archive.getEntry("BOOT-INF/classes/" + WogeAssetsTask.MANIFEST) == null ? "" : "BOOT-INF/classes/";
        ZipEntry manifest = archive.getEntry(prefix + WogeAssetsTask.MANIFEST);
        if (manifest == null) {
            problems.add("Missing packaged Woge asset manifest");
            return;
        }
        String content;
        try (var input = archive.getInputStream(manifest)) {
            content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!expected.equals(content)) {
            problems.add("Packaged Woge asset manifest differs from the current build");
            return;
        }
        Properties entries = new Properties();
        entries.load(new StringReader(content));
        String schema = (String) entries.remove("schemaVersion");
        String bundle = (String) entries.remove("bundleHash");
        if (!"1".equals(schema) || bundle == null || !bundle.matches("[0-9a-f]{64}")) {
            problems.add("Invalid Woge asset manifest schema or hash");
            return;
        }
        Map<String, byte[]> hashes = new TreeMap<>();
        java.util.Set<String> packaged = new java.util.HashSet<>();
        for (String key : entries.stringPropertyNames()) {
            String logical = key.startsWith("asset.") ? key.substring("asset.".length()) : "";
            String url = entries.getProperty(key);
            if (!logical.startsWith("/") || !url.equals(WogeAssetsTask.URL_PREFIX + bundle + logical)) {
                problems.add("Invalid Woge asset manifest entry");
                continue;
            }
            String path = URI.create(url).getPath().substring(WogeAssetsTask.URL_PREFIX.length());
            String resource = prefix + WogeAssetsTask.RESOURCES + path;
            ZipEntry asset = archive.getEntry(resource);
            if (asset == null || asset.isDirectory()) {
                problems.add("Missing packaged asset: " + logical);
            } else {
                packaged.add(resource);
                try (var input = archive.getInputStream(asset)) {
                    hashes.put(logical, WogeAssetsTask.hash(input));
                }
            }
        }
        if (!WogeAssetsTask.bundleHash(hashes).equals(bundle)) {
            problems.add("Packaged asset bytes do not match the content hash");
        }
        archive.stream().filter(entry -> !entry.isDirectory() && entry.getName().startsWith(prefix + WogeAssetsTask.RESOURCES))
                .filter(entry -> !packaged.contains(entry.getName()))
                .forEach(entry -> problems.add("Unregistered packaged asset: " + entry.getName()));
    }
}
