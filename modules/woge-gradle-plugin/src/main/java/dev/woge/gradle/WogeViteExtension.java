package dev.woge.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Settings of the optional Vite adapter ({@code wogeVite { ... }}). The defaults fit most applications;
 * Vite plugins and other options stay in your normal {@code vite.config.*}.
 */
public abstract class WogeViteExtension {
    /** The folder with your entry modules, TypeScript and imported CSS. Default {@code src/main/frontend}. */
    public abstract DirectoryProperty getFrontendDirectory();

    /** Entry modules, as file names in {@link #getFrontendDirectory()}. Default {@code main.ts}. */
    public abstract ListProperty<String> getEntries();

    /** Folder below the static tree for the build output, so {@code main.ts} becomes {@code /vite/main.js}. */
    public abstract Property<String> getOutputPath();

    /** The folder with {@code package.json}, the lockfile and {@code node_modules}. Default: the project folder. */
    public abstract DirectoryProperty getNodeProjectDirectory();

    /** The Node.js executable. Default {@code node} from your {@code PATH}. */
    public abstract Property<String> getNode();

    /** The local port of the Vite dev server that {@code wogeDev} starts. Default 5173. */
    public abstract Property<Integer> getDevPort();

    /** Writes external source maps next to the production files. Off by default. */
    public abstract Property<Boolean> getSourceMaps();
}
