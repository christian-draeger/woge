package dev.woge.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/**
 * Settings for the optional Tailwind adapter, written as {@code wogeTailwind { ... }}.
 *
 * <p>The defaults need no configuration: the input is {@code src/main/tailwind/tailwind.css}, the
 * output is served as {@code /tailwind.css}, and Kotlin plus generated sources are scanned.
 */
public abstract class WogeTailwindExtension {
    /** The Tailwind release this Woge version is tested with. */
    public static final String SUPPORTED_VERSION = "4.3.3";

    /** Your Tailwind CSS: theme, custom utilities and layers. Woge adds the Tailwind import and sources. */
    public abstract RegularFileProperty getInput();

    /** Path of the generated stylesheet below {@code static/}, for example {@code tailwind.css}. */
    public abstract Property<String> getOutputPath();

    /** {@link TailwindExecutor#NPM} by default. */
    public abstract Property<TailwindExecutor> getExecutor();

    /** Directory with {@code package.json} and {@code node_modules} for the npm executor. */
    public abstract DirectoryProperty getNodeProjectDirectory();

    /** An already installed Tailwind executable. When set, Woge runs it instead of npm or a download. */
    public abstract RegularFileProperty getExecutable();

    /** Directories Tailwind scans for class names. Defaults to Kotlin, Java and KSP-generated sources. */
    public abstract ConfigurableFileCollection getSources();

    /** Fails the build on class names assembled at runtime, such as {@code "bg-$tone-500"}. */
    public abstract Property<Boolean> getCheckDynamicClasses();

    /** Uses the project's locked npm package. */
    public void npm() {
        getExecutor().set(TailwindExecutor.NPM);
    }

    /** Uses the pinned standalone executable, so the project needs no Node.js. */
    public void standalone() {
        getExecutor().set(TailwindExecutor.STANDALONE);
    }
}
