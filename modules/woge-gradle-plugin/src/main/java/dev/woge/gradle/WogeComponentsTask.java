package dev.woge.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

/** Explicit source mutations must observe local edits on every invocation. */
@DisableCachingByDefault(because = "Manually installs user-owned sources; never cache or skip local edit checks")
public abstract class WogeComponentsTask extends DefaultTask {
    @Input public abstract Property<String> getOperation();
    @Input public abstract Property<String> getComponentName();
    @Input public abstract Property<String> getVariant();
    @Input public abstract Property<String> getPackageName();
    @Input public abstract Property<String> getWogeVersion();
    @Input public abstract Property<Boolean> getPreview();
    @Input public abstract Property<Boolean> getApply();
    @Input public abstract Property<Boolean> getAcceptLocal();
    @Internal public abstract DirectoryProperty getApplicationDirectory();
    @Internal public abstract DirectoryProperty getReportDirectory();
    @InputDirectory @Optional @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getRegistryDirectory();

    @Option(option = "name", description = "Component name; update all installed items when omitted")
    public void selectComponentName(String value) { getComponentName().set(value); }
    @Option(option = "variant", description = "Explicit css or tailwind variant (default css)")
    public void selectVariant(String value) { getVariant().set(value); }
    @Option(option = "package", description = "Application Kotlin package for installed code")
    public void selectPackageName(String value) { getPackageName().set(value); }
    @Option(option = "preview", description = "Preview without writing sources, lockfile or reports")
    public void selectPreview(boolean value) { getPreview().set(value); }
    @Option(option = "apply", description = "Apply safe updates and write base/local/new conflict snapshots")
    public void selectApply(boolean value) { getApply().set(value); }

    @Option(option = "accept-local", description = "Explicitly accept reviewed local merges and advance the pristine base")
    public void selectAcceptLocal(boolean value) { getAcceptLocal().set(value); }

    @TaskAction
    public void components() {
        try {
            ComponentRegistry registry = new ComponentRegistry(getRegistryDirectory().isPresent()
                    ? getRegistryDirectory().get().getAsFile().toPath() : null);
            if (getOperation().get().equals("list")) {
                registry.items().forEach((name, item) -> getLogger().lifecycle("{} {} {} — {}", name,
                        item.get("version"), ComponentJson.object(item.get("variants")).keySet(), item.get("description")));
                return;
            }
            ComponentInstaller installer = new ComponentInstaller(registry, getApplicationDirectory().get().getAsFile().toPath(),
                    getReportDirectory().get().getAsFile().toPath(), getWogeVersion().get());
            var messages = getOperation().get().equals("add")
                    ? installer.add(getComponentName().get(), getVariant().get(), getPackageName().get(), getPreview().get())
                    : installer.update(getComponentName().get(), getApply().get() && !getPreview().get(), getAcceptLocal().get());
            messages.forEach(message -> getLogger().lifecycle(message));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read or write component sources", exception);
        }
    }
}
