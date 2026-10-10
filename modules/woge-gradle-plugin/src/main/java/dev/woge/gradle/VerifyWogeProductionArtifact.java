package dev.woge.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Fails when the production jar contains Woge development tooling or Spring DevTools. */
@CacheableTask
public abstract class VerifyWogeProductionArtifact extends DefaultTask {
    static final String HEAD_CONTRIBUTION_SERVICE = "META-INF/services/dev.woge.html.DevelopmentHeadContribution";

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getArchive();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getAssetManifest();

    @OutputFile
    public abstract RegularFileProperty getReport();

    @TaskAction
    public void verify() {
        List<String> problems = new ArrayList<>();
        try (ZipFile archive = new ZipFile(getArchive().get().getAsFile())) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                String fileName = name.substring(name.lastIndexOf('/') + 1);
                if (fileName.startsWith("woge-dev-") || fileName.startsWith("spring-boot-devtools")) {
                    problems.add(name);
                } else if (name.equals(HEAD_CONTRIBUTION_SERVICE)) {
                    problems.add(name);
                } else if (name.endsWith(".jar") && containsHeadContribution(archive, entry)) {
                    problems.add(name + "!/" + HEAD_CONTRIBUTION_SERVICE);
                }
                if (getAssetManifest().isPresent()) {
                    WogeAssetVerification.verify(archive,
                            Files.readString(getAssetManifest().get().getAsFile().toPath(), StandardCharsets.UTF_8), problems);
                }
            }
            String report = problems.isEmpty() ? "OK\n" : String.join("\n", problems) + "\n";
            Files.writeString(getReport().get().getAsFile().toPath(), report, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        if (!problems.isEmpty()) {
            throw new GradleException("The production jar failed Woge verification: "
                    + String.join(", ", problems)
                    + ". Keep development tools in 'developmentOnly' and rebuild generated assets before packaging.");
        }
    }

    private static boolean containsHeadContribution(ZipFile archive, ZipEntry jar) throws IOException {
        try (InputStream input = archive.getInputStream(jar); ZipInputStream nested = new ZipInputStream(input)) {
            for (ZipEntry entry = nested.getNextEntry(); entry != null; entry = nested.getNextEntry()) {
                if (entry.getName().equals(HEAD_CONTRIBUTION_SERVICE)) {
                    return true;
                }
            }
        }
        return false;
    }
}
