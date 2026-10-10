package dev.woge.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WogeTailwindTaskTest {
    @TempDir Path root;

    @Test
    void writesAnEntryWithExplicitSourcesAndRunsTheConfiguredExecutable() throws Exception {
        Path code = write("src/main/kotlin/Page.kt", "val card = \"rounded-lg bg-sky-500\"");
        write("src/main/tailwind/tailwind.css", "@theme { --color-brand: var(--woge-brand); }");
        WogeTailwindTask task = task(code.getParent());
        task.generate();
        Path output = root.resolve("build/out/static/tailwind.css");
        String recorded = Files.readString(output);
        assertTrue(recorded.contains("--minify"));
        String entry = Files.readString(task.getTemporaryDir().toPath().resolve("woge-tailwind-entry.css"));
        assertTrue(entry.startsWith("@import \"tailwindcss\" source(none);\n"));
        assertTrue(entry.contains("@import \"" + slashes(root.resolve("src/main/tailwind/tailwind.css")) + "\";"));
        assertTrue(entry.contains("@source \"" + slashes(code.getParent()) + "\";"));
        assertFalse(entry.contains("missing"), "missing source directories are skipped");
    }

    @Test
    void removesStaleOutputWhenTheOutputPathChanges() throws Exception {
        Path code = write("src/main/kotlin/Page.kt", "val x = \"p-4\"");
        write("src/main/tailwind/tailwind.css", "");
        WogeTailwindTask task = task(code.getParent());
        task.generate();
        task.getOutputPath().set("css/app.css");
        task.generate();
        assertFalse(Files.exists(root.resolve("build/out/static/tailwind.css")));
        assertTrue(Files.exists(root.resolve("build/out/static/css/app.css")));
    }

    @Test
    void rejectsClassNamesBuiltAtRuntimeWithFileAndLine() throws Exception {
        Path code = write("src/main/kotlin/Badge.kt", "fun badge(tone: String) =\n  \"rounded bg-$tone-500\"\n");
        write("src/main/tailwind/tailwind.css", "");
        GradleException error = assertThrows(GradleException.class, () -> task(code.getParent()).generate());
        assertTrue(error.getMessage().contains("src/main/kotlin/Badge.kt:2"), error.getMessage());
        assertTrue(error.getMessage().contains("checkDynamicClasses = false"));
    }

    @Test
    void dynamicCheckFindsInterpolationAndConcatenationButNotFullNames() throws IOException {
        Path file = write("src/main/kotlin/Classes.kt", String.join("\n",
                "val a = \"hover:text-${tone}-700\"",
                "val b = \"px-\" + size",
                "val c = \"text-sky-700 px-4 $extra\"",
                "val d = \"md:grid-cols-$columns\"",
                "val e = \"Total: $count\"",
                "// \"bg-$tone\" is explained in a comment",
                " * KDoc: \"bg-$tone-100\" would be invisible"));
        List<String> findings = WogeTailwindTask.dynamicClasses(file, root);
        assertEquals(3, findings.size(), findings.toString());
        assertTrue(findings.get(0).startsWith("src/main/kotlin/Classes.kt:1:"));
        assertTrue(findings.get(1).startsWith("src/main/kotlin/Classes.kt:2:"));
        assertTrue(findings.get(2).startsWith("src/main/kotlin/Classes.kt:4:"));
    }

    @Test
    void rejectsAnInputThatImportsTailwindItself() throws Exception {
        Path code = write("src/main/kotlin/Page.kt", "");
        write("src/main/tailwind/tailwind.css", "@import \"tailwindcss\";");
        GradleException error = assertThrows(GradleException.class, () -> task(code.getParent()).generate());
        assertTrue(error.getMessage().contains("remove `@import \"tailwindcss\"`"));
    }

    @Test
    void explainsHowToInstallTailwindWhenNodeModulesAreMissing() throws Exception {
        Path code = write("src/main/kotlin/Page.kt", "");
        write("src/main/tailwind/tailwind.css", "");
        WogeTailwindTask task = task(code.getParent());
        task.getExecutable().unset();
        GradleException error = assertThrows(GradleException.class, task::generate);
        assertTrue(error.getMessage().contains("npm install --save-dev --save-exact tailwindcss@4.3.3"));
        assertTrue(error.getMessage().contains("standalone()"));
    }

    @Test
    void surfacesTailwindErrors() throws Exception {
        Path code = write("src/main/kotlin/Page.kt", "");
        write("src/main/tailwind/tailwind.css", "");
        WogeTailwindTask task = task(code.getParent());
        task.getExecutable().set(script("broken", "echo 'Cannot apply unknown utility class' >&2\nexit 3\n").toFile());
        GradleException error = assertThrows(GradleException.class, task::generate);
        assertTrue(error.getMessage().contains("exit code 3"));
        assertTrue(error.getMessage().contains("Cannot apply unknown utility class"));
    }

    @Test
    void mapsPlatformsToPinnedStandaloneExecutables() {
        assertEquals("tailwindcss-macos-arm64", WogeTailwindTask.standaloneAsset("Mac OS X", "aarch64"));
        assertEquals("tailwindcss-linux-x64", WogeTailwindTask.standaloneAsset("Linux", "amd64"));
        assertEquals("tailwindcss-windows-x64.exe", WogeTailwindTask.standaloneAsset("Windows 11", "amd64"));
        assertThrows(GradleException.class, () -> WogeTailwindTask.standaloneAsset("FreeBSD", "amd64"));
        WogeTailwindTask.STANDALONE_SHA256.values().forEach(hash -> assertTrue(hash.matches("[0-9a-f]{64}")));
    }

    @Test
    void pluginRegistersTheExtensionWithNpmAndTheDefaultInput() {
        Project project = ProjectBuilder.builder().withProjectDir(root.toFile()).build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(WogeTailwindPlugin.class);
        WogeTailwindExtension extension = project.getExtensions().getByType(WogeTailwindExtension.class);
        assertEquals(TailwindExecutor.NPM, extension.getExecutor().get());
        extension.standalone();
        assertEquals(TailwindExecutor.STANDALONE, extension.getExecutor().get());
        assertEquals(project.file("src/main/tailwind/tailwind.css"), extension.getInput().get().getAsFile());
    }

    private WogeTailwindTask task(Path sources) throws IOException {
        Project project = ProjectBuilder.builder().withProjectDir(root.toFile()).build();
        WogeTailwindTask task = project.getTasks().register("wogeTailwind", WogeTailwindTask.class).get();
        task.getInput().set(root.resolve("src/main/tailwind/tailwind.css").toFile());
        task.getSources().from(sources.toFile(), root.resolve("missing").toFile());
        task.getOutputPath().set("tailwind.css");
        task.getExecutor().set(TailwindExecutor.NPM);
        task.getVersion().set(WogeTailwindExtension.SUPPORTED_VERSION);
        task.getCheckDynamicClasses().set(true);
        task.getNodeProjectDirectory().set(root.toFile());
        task.getToolCache().set(root.resolve("cache").toFile());
        task.getOutputDirectory().set(root.resolve("build/out").toFile());
        task.getExecutable().set(script("tailwindcss", """
                out=""
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = "--output" ]; then out="$2"; shift; fi
                  args="$args $1"; shift
                done
                printf '%s' "$args" > "$out"
                """).toFile());
        return task;
    }

    private Path script(String name, String body) throws IOException {
        assumeFalse(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"));
        Path file = root.resolve("bin").resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "#!/bin/sh\n" + body);
        assertTrue(file.toFile().setExecutable(true));
        return file;
    }

    private Path write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private static String slashes(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }
}
