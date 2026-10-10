package dev.woge.gradle;

import java.util.List;

/**
 * The supported Gradle workflow of a Woge application. Task descriptions, {@code ./gradlew wogeTasks}
 * and its JSON output all come from this one list, so humans and coding agents read the same help.
 */
final class WogeWorkflow {
    record Option(String name, String description) {}

    record Step(String task, String phase, String summary, List<Option> options) {
        String command() {
            return "./gradlew " + task;
        }
    }

    static final Step DEVELOP = new Step(
            WogeSpringBootPlugin.DEV_TASK,
            "development",
            "Runs the application with live reload: rebuilds on save, restarts the server and refreshes browsers.",
            List.of(
                    new Option("--port=<port>", "The local application port (default 8080)."),
                    new Option("--full-restart", "Always restart the whole application process.")));

    static final Step VERIFY_ARTIFACT = new Step(
            WogeSpringBootPlugin.VERIFY_TASK,
            "verification",
            "Checks production assets and verifies that the jar contains no Woge development tooling.",
            List.of());

    static final List<Step> STEPS = List.of(
            DEVELOP,
            new Step("test", "verification", "Runs the application's tests.", List.of()),
            new Step("check", "verification",
                    "Runs tests and all checks, including " + WogeSpringBootPlugin.VERIFY_TASK + ".", List.of()),
            VERIFY_ARTIFACT,
            new Step("wogeManifest", "documentation",
                    "Writes non-secret compiler metadata to .woge/manifest.json.", List.of()),
            new Step("wogeAssets", "production",
                    "Packages static files under content-hashed URLs without Node or Vite.", List.of()),
            new Step("bootJar", "production", "Builds the runnable production jar without development tooling.",
                    List.of()),
            new Step("bootRun", "production",
                    "Runs the application once like production, without live reload.", List.of()),
            new Step(WogeSpringBootPlugin.HELP_TASK, "help",
                    "Lists this workflow. Use --format=json for coding agents.",
                    List.of(new Option("--format=<text|json>", "Output as readable text (default) or JSON."))));

    private WogeWorkflow() {}

    static String text() {
        StringBuilder out = new StringBuilder("Woge application workflow\n");
        for (Step step : STEPS) {
            out.append('\n').append(step.command()).append("  [").append(step.phase()).append("]\n");
            out.append("    ").append(step.summary()).append('\n');
            for (Option option : step.options()) {
                out.append("    ").append(option.name()).append("  ").append(option.description()).append('\n');
            }
        }
        return out.toString();
    }

    static String json() {
        StringBuilder out = new StringBuilder("{\"schema\":\"woge-workflow/v1\",\"tasks\":[");
        for (int index = 0; index < STEPS.size(); index++) {
            Step step = STEPS.get(index);
            if (index > 0) {
                out.append(',');
            }
            out.append("{\"task\":").append(quote(step.task()))
                    .append(",\"command\":").append(quote(step.command()))
                    .append(",\"phase\":").append(quote(step.phase()))
                    .append(",\"summary\":").append(quote(step.summary()))
                    .append(",\"options\":[");
            for (int optionIndex = 0; optionIndex < step.options().size(); optionIndex++) {
                Option option = step.options().get(optionIndex);
                if (optionIndex > 0) {
                    out.append(',');
                }
                out.append("{\"name\":").append(quote(option.name()))
                        .append(",\"description\":").append(quote(option.description())).append('}');
            }
            out.append("]}");
        }
        return out.append("]}\n").toString();
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                default -> {
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
