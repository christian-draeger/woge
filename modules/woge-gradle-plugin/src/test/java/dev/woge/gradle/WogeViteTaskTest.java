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
import org.gradle.api.plugins.JavaApplication;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WogeViteTaskTest {
    @TempDir Path root;

    @Test
    void runsViteWithWogeOwnedPathsAndClearsStaleOutput() throws Exception {
        write("src/main/frontend/main.ts", "console.log('hi')");
        write("vite.config.mts", "export default {}");
        write("build/out/vite/old.js", "stale");
        WogeViteTask task = task(fakeNode("""
                env | grep '^WOGE_VITE_' | sort > "$WOGE_VITE_OUT_DIR.env"
                mkdir -p "$WOGE_VITE_OUT_DIR" && echo built > "$WOGE_VITE_OUT_DIR/main.js"
                """));
        task.build();
        assertFalse(Files.exists(root.resolve("build/out/vite/old.js")));
        assertTrue(Files.exists(root.resolve("build/out/vite/main.js")));
        String env = Files.readString(root.resolve("build/out/vite.env"));
        assertTrue(env.contains("WOGE_VITE_COMMAND=build"), env);
        assertTrue(env.contains("WOGE_VITE_ENTRIES=main.ts"), env);
        assertTrue(env.contains("WOGE_VITE_ROOT=" + root.resolve("src/main/frontend")), env);
        assertTrue(env.contains("WOGE_VITE_CONFIG=" + root.resolve("vite.config.mts")), env);
        assertTrue(env.contains("WOGE_VITE_SOURCEMAP=false"), env);
    }

    @Test
    void explainsAMissingEntry() throws Exception {
        GradleException error = assertThrows(GradleException.class, () -> task(fakeNode("exit 0")).build());
        assertTrue(error.getMessage().contains("main.ts does not exist"), error.getMessage());
    }

    @Test
    void explainsAMissingNodeAndAFailedBuild() throws Exception {
        write("src/main/frontend/main.ts", "");
        WogeViteTask missing = task(root.resolve("no-node").toString());
        assertTrue(assertThrows(GradleException.class, missing::build).getMessage().contains("wogeVite { node ="));
        WogeViteTask failing = task(fakeNode("exit 4"));
        assertTrue(assertThrows(GradleException.class, failing::build).getMessage().contains("exit code 4"));
    }

    @Test
    void theScriptOnlyUsesTheProjectsOwnVite() {
        String script = WogeViteTask.script();
        assertTrue(script.contains("await import(\"vite\")"));
        assertTrue(script.contains("npm install --save-dev vite"));
        assertEquals(List.of("node", "--input-type=module", "--eval", script), WogeViteTask.command("node"));
    }

    @Test
    void rejectsOutputPathsOutsideTheStaticTree() {
        assertEquals("assets/vite", WogeViteTask.validOutputPath("assets/vite"));
        for (String invalid : List.of("../vite", "/vite", "vite/", "")) {
            assertThrows(GradleException.class, () -> WogeViteTask.validOutputPath(invalid), invalid);
        }
    }

    @Test
    void pluginGivesWogeDevTheViteDevServer() {
        Project project = ProjectBuilder.builder().withProjectDir(root.toFile()).build();
        project.getPluginManager().apply(WogeVitePlugin.class);
        project.getPluginManager().apply("application");
        project.getExtensions().getByType(JavaApplication.class).getMainClass().set("example.ApplicationKt");
        WogeDevTask dev = (WogeDevTask) project.getTasks().getByName(WogeSpringBootPlugin.DEV_TASK);

        assertEquals("node", dev.getViteCommand().get().get(0));
        assertEquals(5173, dev.getVitePort().get());
        assertEquals("serve", dev.getViteEnvironment().get().get("WOGE_VITE_COMMAND"));
        assertEquals(project.file("src/main/frontend").toString(), dev.getViteEnvironment().get().get("WOGE_VITE_ROOT"));
        assertTrue(dev.getBuildCommand().get().contains("-PwogeDevelopment=true"));
    }

    private WogeViteTask task(String node) {
        Project project = ProjectBuilder.builder().withProjectDir(root.toFile()).build();
        WogeViteTask task = project.getTasks().register("wogeVite", WogeViteTask.class).get();
        task.getFrontendDirectory().set(root.resolve("src/main/frontend").toFile());
        task.getNodeProjectDirectory().set(root.toFile());
        task.getEntries().set(List.of("main.ts"));
        task.getOutputPath().set("vite");
        task.getSourceMaps().set(false);
        task.getNode().set(node);
        task.getOutputDirectory().set(root.resolve("build/out").toFile());
        return task;
    }

    private String fakeNode(String body) throws IOException {
        assumeFalse(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"));
        Path file = root.resolve("bin/node");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "#!/bin/sh\n" + body);
        assertTrue(file.toFile().setExecutable(true));
        return file.toString();
    }

    private void write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
