package dev.woge.baseline.shared

import java.time.LocalDate
import java.time.OffsetDateTime

data class ProjectIdentity(
    val slug: String,
    val name: String,
)

data class ProjectTask(
    val id: Long,
    val title: String,
    val owner: String?,
    val dueDate: LocalDate?,
    val completed: Boolean,
)

data class ProjectActivity(
    val id: Long,
    val description: String,
    val occurredAt: OffsetDateTime,
)

data class ProjectSnapshot(
    val project: ProjectIdentity,
    val tasks: List<ProjectTask>,
    val activity: List<ProjectActivity>,
) {
    val openTaskCount: Int = tasks.count { !it.completed }
    val completedTaskCount: Int = tasks.count { it.completed }
    val overdueTaskCount: Int = tasks.count {
        !it.completed && it.dueDate?.isBefore(LocalDate.now()) == true
    }
}

sealed interface CreateTaskResult {
    data class Created(val snapshot: ProjectSnapshot) : CreateTaskResult

    data object BlankTitle : CreateTaskResult
}

class ReferenceProjectStore {
    private val project = ProjectIdentity(slug = "woge", name = "Woge")
    private val tasks = linkedMapOf(
        1L to ProjectTask(
            id = 1,
            title = "Define the walking skeleton",
            owner = "Mara",
            dueDate = LocalDate.now().plusDays(2),
            completed = false,
        ),
        2L to ProjectTask(
            id = 2,
            title = "Record the web-native boundary",
            owner = "Noah",
            dueDate = LocalDate.now().minusDays(1),
            completed = true,
        ),
    )
    private val activity = mutableListOf(
        ProjectActivity(
            id = 1,
            description = "Reference project created",
            occurredAt = OffsetDateTime.now().minusHours(2),
        ),
    )
    private var nextTaskId = 3L
    private var nextActivityId = 2L

    @Synchronized
    fun identity(slug: String): ProjectIdentity? = project.takeIf { it.slug == slug }

    @Synchronized
    fun snapshot(slug: String): ProjectSnapshot? =
        project.takeIf { it.slug == slug }?.let(::snapshot)

    @Synchronized
    fun createTask(
        slug: String,
        title: String,
        owner: String?,
        dueDate: LocalDate?,
    ): CreateTaskResult? {
        if (slug != project.slug) return null
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty()) return CreateTaskResult.BlankTitle

        val task = ProjectTask(
            id = nextTaskId++,
            title = normalizedTitle,
            owner = owner?.trim()?.takeIf(String::isNotEmpty),
            dueDate = dueDate,
            completed = false,
        )
        tasks[task.id] = task
        recordActivity("Task created: ${task.title}")
        return CreateTaskResult.Created(snapshot(project))
    }

    @Synchronized
    fun setCompleted(slug: String, taskId: Long, completed: Boolean): ProjectSnapshot? {
        if (slug != project.slug) return null
        val task = tasks[taskId] ?: return null
        tasks[taskId] = task.copy(completed = completed)
        recordActivity("Task ${if (completed) "completed" else "reopened"}: ${task.title}")
        return snapshot(project)
    }

    private fun recordActivity(description: String) {
        activity.add(
            0,
            ProjectActivity(
                id = nextActivityId++,
                description = description,
                occurredAt = OffsetDateTime.now(),
            ),
        )
    }

    private fun snapshot(identity: ProjectIdentity) = ProjectSnapshot(
        project = identity,
        tasks = tasks.values.toList(),
        activity = activity.toList(),
    )
}

data class BaselineDelays(
    val summaryMillis: Long,
    val tasksMillis: Long,
    val activityMillis: Long,
)
