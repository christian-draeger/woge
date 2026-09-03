package dev.woge.spike.references.negative

import dev.woge.spike.references.ProjectPage
import dev.woge.spike.references.TaskStatusView
import dev.woge.spike.references.UpdateBuilder

public fun invalidPageTarget() {
    UpdateBuilder().replace(ProjectPage, TaskStatusView(completed = true))
}
