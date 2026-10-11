package dev.woge.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WogeKotlinJsIslandsTaskTest {
    @TempDir Path root;

    @Test
    void copiesTheProductionGraphAndGeneratesVersionedTypedRegistration() throws IOException {
        Path input = root.resolve("production");
        Files.createDirectories(input.resolve("chunks"));
        Files.writeString(input.resolve("notes.mjs"), "export function mount() {}\n");
        Files.writeString(input.resolve("chunks/shared.mjs"), "export const answer = 42;\n");
        WogeKotlinJsIslandsTask task = task(input);
        task.getIslands().set(List.of("notes|notes.mjs|mount"));
        task.registerModules();

        String generated = Files.readString(output().resolve("java/dev/woge/generated/WogeIslandModules.java"));
        assertTrue(generated.contains("IslandModule notes(IslandType<?> type)"));
        assertTrue(generated.contains("/assets/woge/islands/"));
        assertTrue(generated.contains("\"mount\""));
        int versionStart = generated.indexOf("/assets/woge/islands/") + "/assets/woge/islands/".length();
        String version = generated.substring(versionStart, versionStart + 64);
        assertTrue(version.matches("[0-9a-f]{64}"));
        assertTrue(Files.exists(output().resolve("static/assets/woge/islands/" + version + "/notes.mjs")));
        assertTrue(Files.exists(output().resolve("static/assets/woge/islands/" + version + "/chunks/shared.mjs")));
        String report = Files.readString(output().resolve("island-module-sizes.json"));
        assertTrue(report.contains("\"raw\":"));
        assertTrue(report.contains("\"gzip\":"));
        assertTrue(report.contains("\"brotli\":"));

        Files.writeString(input.resolve("notes.mjs"), "export function mount(root) {}\n");
        task.registerModules();
        String changed = Files.readString(output().resolve("java/dev/woge/generated/WogeIslandModules.java"));
        assertFalse(generated.equals(changed));
    }

    private WogeKotlinJsIslandsTask task(Path input) {
        var project = ProjectBuilder.builder().withProjectDir(root.toFile()).build();
        WogeKotlinJsIslandsTask task = project.getTasks().register("islands", WogeKotlinJsIslandsTask.class).get();
        task.getModulesDirectory().set(input.toFile());
        task.getOutputDirectory().set(output().toFile());
        return task;
    }

    private Path output() {
        return root.resolve("build/generated/woge-islands");
    }
}
