package dev.woge.baseline.mvc

import dev.woge.baseline.shared.BaselineDelays
import dev.woge.baseline.shared.CreateTaskResult
import dev.woge.baseline.shared.ProjectIdentity
import dev.woge.baseline.shared.ProjectSnapshot
import dev.woge.baseline.shared.ReferenceProjectStore
import jakarta.servlet.http.HttpServletResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

@Controller
class ProjectController(
    private val store: ReferenceProjectStore,
    private val delays: BaselineDelays,
) {
    @GetMapping("/projects/{project}")
    fun project(
        @PathVariable project: String,
        @RequestParam(defaultValue = "false") full: Boolean,
        model: Model,
    ): String {
        val identity = requireProject(project)
        populatePage(model, identity, if (full) requireSnapshot(project) else null)
        return "project"
    }

    @GetMapping("/projects/{project}/regions/summary")
    fun summary(@PathVariable project: String, model: Model): String {
        pause(delays.summaryMillis)
        model.addAttribute("snapshot", requireSnapshot(project))
        model.addAttribute("oob", false)
        return "fragments/project-summary"
    }

    @GetMapping("/projects/{project}/regions/tasks")
    fun tasks(@PathVariable project: String, model: Model): String {
        pause(delays.tasksMillis)
        model.addAttribute("snapshot", requireSnapshot(project))
        model.addAttribute("oob", false)
        return "fragments/task-table"
    }

    @GetMapping("/projects/{project}/regions/activity")
    fun activity(@PathVariable project: String, model: Model): String {
        pause(delays.activityMillis)
        model.addAttribute("snapshot", requireSnapshot(project))
        model.addAttribute("oob", false)
        return "fragments/activity"
    }

    @PostMapping("/projects/{project}/tasks")
    fun createTask(
        @PathVariable project: String,
        @RequestParam(defaultValue = "") title: String,
        @RequestParam(required = false) owner: String?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        dueDate: LocalDate?,
        @RequestHeader(name = "HX-Request", defaultValue = "false") enhanced: Boolean,
        response: HttpServletResponse,
        model: Model,
    ): String = when (val result = store.createTask(project, title, owner, dueDate)) {
        null -> throw notFound(project)
        CreateTaskResult.BlankTitle -> {
            response.status = 422
            if (enhanced) {
                populateForm(model, requireProject(project), title, owner, dueDate?.toString(), "Enter a task title.")
                "fragments/task-form"
            } else {
                populatePage(
                    model = model,
                    project = requireProject(project),
                    snapshot = requireSnapshot(project),
                    titleValue = title,
                    ownerValue = owner.orEmpty(),
                    dueDateValue = dueDate?.toString().orEmpty(),
                    titleError = "Enter a task title.",
                )
                "project"
            }
        }
        is CreateTaskResult.Created -> if (enhanced) {
            model.addAttribute("snapshot", result.snapshot)
            "actions/create-result"
        } else {
            "redirect:/projects/$project?full=true"
        }
    }

    @PostMapping("/projects/{project}/tasks/{task}/status")
    fun setTaskStatus(
        @PathVariable project: String,
        @PathVariable task: Long,
        @RequestParam completed: Boolean,
        @RequestHeader(name = "HX-Request", defaultValue = "false") enhanced: Boolean,
        model: Model,
    ): String {
        val snapshot = store.setCompleted(project, task, completed) ?: throw notFound(project)
        return if (enhanced) {
            model.addAttribute("snapshot", snapshot)
            "actions/status-result"
        } else {
            "redirect:/projects/$project?full=true"
        }
    }

    private fun requireProject(project: String) = store.identity(project) ?: throw notFound(project)

    private fun requireSnapshot(project: String) = store.snapshot(project) ?: throw notFound(project)

    private fun notFound(project: String) =
        ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown project: $project")

    private fun pause(millis: Long) {
        if (millis > 0) Thread.sleep(millis)
    }
}

private fun populatePage(
    model: Model,
    project: ProjectIdentity,
    snapshot: ProjectSnapshot?,
    titleValue: String = "",
    ownerValue: String = "",
    dueDateValue: String = "",
    titleError: String? = null,
) {
    model.addAttribute("snapshot", snapshot)
    populateForm(model, project, titleValue, ownerValue, dueDateValue, titleError)
}

private fun populateForm(
    model: Model,
    project: ProjectIdentity,
    titleValue: String,
    ownerValue: String?,
    dueDateValue: String?,
    titleError: String?,
) {
    model.addAttribute("project", project)
    model.addAttribute("titleValue", titleValue)
    model.addAttribute("ownerValue", ownerValue.orEmpty())
    model.addAttribute("dueDateValue", dueDateValue.orEmpty())
    model.addAttribute("titleError", titleError)
}
