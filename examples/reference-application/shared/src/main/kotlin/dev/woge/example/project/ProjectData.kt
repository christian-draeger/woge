package dev.woge.example.project

internal data class ProjectSnapshot(
    val slug: String,
    val name: String,
    val tasks: List<ProjectTask>,
    val activity: List<ProjectActivity>,
)

internal data class ProjectTask(
    val title: String,
    val owner: String,
    val status: String,
)

internal data class ProjectActivity(
    val description: String,
    val date: String,
)

internal val REFERENCE_PROJECT: ProjectSnapshot =
    ProjectSnapshot(
        slug = "woge",
        name = "Woge",
        tasks =
            listOf(
                ProjectTask("Publish the first web-first guide", "Ada", "In progress"),
                ProjectTask("Verify the deferred browser path", "Lin", "Open"),
                ProjectTask("Record the architecture decision", "Sam", "Complete"),
            ),
        activity =
            listOf(
                ProjectActivity("Standards-native CSS contract implemented", "2026-09-05"),
                ProjectActivity("Spring Boot adapter selected", "2026-09-05"),
            ),
    )
