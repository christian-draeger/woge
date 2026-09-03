package dev.woge.spike.references.negative

import dev.woge.spike.references.ProjectCard
import dev.woge.spike.references.ProjectCardProps
import dev.woge.spike.references.ProjectSlug
import dev.woge.spike.references.TaskRow
import dev.woge.spike.references.TaskStatusView

public fun invalidRegionOwner() {
    val projectCard = ProjectCard.instance(ProjectSlug("woge"), ProjectCardProps("Woge"))
    TaskRow.status.of(projectCard, TaskStatusView(completed = true))
}
