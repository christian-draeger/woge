package dev.woge.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

/** Prints the supported Woge workflow as text for people or as JSON for coding agents. */
@DisableCachingByDefault(because = "Prints help to the console")
public abstract class WogeTasksTask extends DefaultTask {
    @Input
    @Option(option = "format", description = "Output as readable text (default) or JSON.")
    public abstract Property<String> getFormat();

    @TaskAction
    public void print() {
        String format = getFormat().get();
        String output = switch (format) {
            case "text" -> WogeWorkflow.text();
            case "json" -> WogeWorkflow.json();
            default -> throw new GradleException("Unknown --format '" + format + "'. Use text or json.");
        };
        System.out.print(output);
    }
}
