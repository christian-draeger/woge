package dev.woge.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.gradle.api.GradleException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WogeAssetsTaskTest {
    @TempDir Path root;

    @Test
    void packagesRelativeCssReferencesUnderOneReproducibleHash() throws IOException {
        Path css = write("css/site.css", "body { background: url('../images/logo.svg'); }");
        Path image = write("images/logo.svg", "<svg/>");
        WogeAssetsTask task = task();
        task.getSourceFiles().from(image, css);
        task.generate();
        String original = manifest();
        Properties metadata = properties();
        String bundle = metadata.getProperty("bundleHash");
        assertTrue(bundle.matches("[0-9a-f]{64}"));
        assertEquals("/_woge/assets/" + bundle + "/css/site.css", metadata.getProperty("asset./css/site.css"));
        assertEquals(Files.readString(css), Files.readString(output().resolve(WogeAssetsTask.RESOURCES + bundle + "/css/site.css")));
        assertTrue(Files.exists(output().resolve(WogeAssetsTask.RESOURCES + bundle + "/images/logo.svg")));
        task.getSourceFiles().setFrom(css, image);
        task.generate();
        assertEquals(original, manifest());
        Files.writeString(image, "<svg>changed</svg>");
        task.generate();
        assertFalse(bundle.equals(properties().getProperty("bundleHash")));
        assertFalse(Files.exists(output().resolve(WogeAssetsTask.RESOURCES + bundle)));
    }

    @Test
    void deletionAndEmptyTreesDoNotLeaveStaleAssets() throws IOException {
        Path css = write("site.css", "body {}");
        WogeAssetsTask task = task();
        task.getSourceFiles().from(css);
        task.generate();
        task.getSourceFiles().setFrom();
        task.generate();
        assertEquals(2, properties().size());
        try (var files = Files.walk(output())) {
            assertEquals(1, files.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void assetUrlsEncodeSpacesUnicodeAndPropertySyntax() throws IOException {
        Path image = write("images/a = ü*.svg", "binary");
        WogeAssetsTask task = task();
        task.getSourceFiles().from(image);
        task.generate();
        String key = "asset./images/a%20%3D%20%C3%BC%2A.svg";
        assertTrue(properties().containsKey(key));
        assertFalse(manifest().contains("ü"));
    }

    @Test
    void renamesChangeLogicalInputsAndBundleWithoutChangingBytes() throws IOException {
        Path first = write("first/site.css", "body {}");
        WogeAssetsTask task = task();
        task.getSourceFiles().from(first);
        task.generate();
        String original = properties().getProperty("bundleHash");
        assertEquals(java.util.List.of("/first/site.css"), task.getLogicalPaths());
        Path renamed = write("second/site.css", "body {}");
        task.getSourceFiles().setFrom(renamed);
        task.generate();
        assertEquals(java.util.List.of("/second/site.css"), task.getLogicalPaths());
        assertFalse(original.equals(properties().getProperty("bundleHash")));
        assertFalse(properties().containsKey("asset./first/site.css"));
    }

    @Test
    void unsafeOutputAndOverlappingInputFailBeforeDeletingFiles() throws IOException {
        Path css = write("site.css", "body {}");
        WogeAssetsTask task = task();
        task.getSourceFiles().from(css);
        task.getOutputDirectory().set(root.toFile());
        assertThrows(GradleException.class, task::generate);
        assertTrue(Files.exists(css));
        task.getSourceDirectory().set(root.toFile());
        task.getOutputDirectory().set(output().toFile());
        assertThrows(GradleException.class, task::generate);
        assertTrue(Files.exists(css));
    }

    @Test
    void symlinksAndExternalInputsFailWithoutLeavingASuccessManifest() throws IOException {
        Path css = write("site.css", "body {}");
        WogeAssetsTask task = task();
        task.getSourceFiles().from(css);
        task.generate();
        Path external = root.resolve("private.txt");
        Files.writeString(external, "private");
        task.getSourceFiles().from(external);
        assertThrows(GradleException.class, task::generate);
        assertFalse(Files.exists(output().resolve(WogeAssetsTask.MANIFEST)));
        Path link = root.resolve("static/link.css");
        Files.createSymbolicLink(link, external);
        task.getSourceFiles().setFrom(link);
        assertThrows(GradleException.class, task::generate);
        assertFalse(Files.exists(output().resolve(WogeAssetsTask.MANIFEST)));
    }

    private Path write(String relative, String content) throws IOException {
        Path file = root.resolve("static").resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private Path output() {
        return root.resolve("build/generated/woge-assets/resources");
    }

    private String manifest() throws IOException {
        return Files.readString(output().resolve(WogeAssetsTask.MANIFEST));
    }

    private Properties properties() throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(manifest()));
        return properties;
    }

    private WogeAssetsTask task() {
        var project = ProjectBuilder.builder().withProjectDir(root.toFile()).build();
        WogeAssetsTask task = project.getTasks().register("assets", WogeAssetsTask.class).get();
        task.getSourceDirectory().set(root.resolve("static").toFile());
        task.getOutputDirectory().set(output().toFile());
        return task;
    }
}
