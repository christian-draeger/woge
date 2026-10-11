package dev.woge.gradle;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Action;
import org.gradle.api.Project;

/** Opt-in production assets for browser islands. */
public final class WogeIslandsExtension {
    private final List<String> islands = new ArrayList<>();
    private Project kotlinJsProject;

    public WogeIslandsExtension() {}

    /** Selects a Kotlin/JS IR project and configures the module exports used as island mounts. */
    public void kotlinJs(Project moduleProject, Action<WogeKotlinJsIslands> configure) {
        if (kotlinJsProject != null) {
            throw new IllegalStateException("Configure one Kotlin/JS island project per application");
        }
        kotlinJsProject = moduleProject;
        WogeKotlinJsIslands islandsDsl = new WogeKotlinJsIslands(islands);
        configure.execute(islandsDsl);
    }

    Project getKotlinJsProject() {
        return kotlinJsProject;
    }

    List<String> getIslands() {
        return List.copyOf(islands);
    }

    /** Defines a generated, typed factory for one named module. */
    public static final class WogeKotlinJsIslands {
        private final List<String> islands;

        private WogeKotlinJsIslands(List<String> islands) {
            this.islands = islands;
        }

        public void island(String generatedName, String moduleFile, String mountExport) {
            if (!moduleFile.matches("(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-]+\\.m?js")) {
                throw new IllegalArgumentException("Island module must be a relative .js or .mjs output path");
            }
            islands.add(generatedName + "|" + moduleFile + "|" + mountExport);
        }
    }
}
