package dev.woge.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/** Source ownership settings. No runtime dependency or external process is installed. */
public abstract class WogeComponentsExtension {
    public abstract Property<String> getPackageName();
    public abstract Property<String> getWogeVersion();
    public abstract DirectoryProperty getRegistryDirectory();
}
