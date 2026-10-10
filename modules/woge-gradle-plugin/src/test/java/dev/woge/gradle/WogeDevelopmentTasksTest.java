package dev.woge.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WogeDevelopmentTasksTest {
    @TempDir Path root;

    @Test
    void ktorApplicationsGetWogeDevWithTheApplicationMainClass() {
        Project project = ProjectBuilder.builder().withProjectDir(root.toFile()).build();
        project.getPluginManager().apply(WogeApplicationPlugin.class);
        project.getPluginManager().apply("application");
        project.getExtensions().getByType(JavaApplication.class).getMainClass().set("example.ApplicationKt");

        WogeDevTask task = (WogeDevTask) project.getTasks().getByName(WogeSpringBootPlugin.DEV_TASK);

        assertEquals("ktor", task.getHost().get());
        assertEquals("example.ApplicationKt", task.getApplicationMainClass().get());
        assertFalse(task.getFastRestart().get());
        List<String> build = task.getBuildCommand().get();
        assertEquals(":classes", build.get(build.size() - 1));
        assertFalse(build.contains(":resolveMainClassName"));
        assertTrue(task.getMainClassFile().get().getAsFile().toPath().startsWith(
                task.getStateDirectory().get().getAsFile().toPath()));
        assertNotNull(project.getConfigurations().findByName(WogeDevelopmentTasks.RUNTIME_CONFIGURATION));
        assertTrue(project.getConfigurations().getByName(WogeDevelopmentTasks.RUNTIME_CONFIGURATION)
                .getDependencies().stream().anyMatch(dependency -> "woge-dev-client".equals(dependency.getName())));
    }

    @Test
    void applicationsWithoutTheApplicationPluginGetNoWogeDev() {
        Project project = ProjectBuilder.builder().withProjectDir(root.toFile()).build();
        project.getPluginManager().apply(WogeApplicationPlugin.class);
        project.getPluginManager().apply("java");

        assertNull(project.getTasks().findByName(WogeSpringBootPlugin.DEV_TASK));
    }
}
