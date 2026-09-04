package dev.woge.ui.registry.projectboard

import dev.woge.ui.headless.ProjectBoardModel
import dev.woge.ui.headless.ProjectStatus
import dev.woge.ui.headless.escapeAttribute
import dev.woge.ui.headless.escapeText

public enum class ProjectBoardStyle(
    public val stylesheet: String,
    public val wrapperClasses: String,
) {
    PLAIN("/assets/components/project-board.css", "project-board-recipe project-board-recipe--aurora"),
    TAILWIND(
        "/assets/tailwind.min.css",
        "rounded-xl border-2 border-emerald-500 bg-white p-6 shadow-lg dark:bg-slate-950",
    ),
}

public object SourceOwnedProjectBoard {
    public fun render(
        model: ProjectBoardModel,
        style: ProjectBoardStyle = ProjectBoardStyle.PLAIN,
    ): String = buildString {
        val averageProgress = model.projects.map { it.progress }.average().takeUnless { it.isNaN() } ?: 0.0
        append("<article data-owned-component=\"project-board\" data-style=\"")
        append(style.name.lowercase())
        append("\" data-stylesheet=\"")
        append(style.stylesheet)
        append("\" class=\"")
        append(style.wrapperClasses)
        append("\" aria-labelledby=\"owned-")
        append(model.instanceKey)
        append("-heading\">")
        append("<header><p>Aurora delivery portfolio</p><h2 id=\"owned-")
        append(model.instanceKey)
        append("-heading\">")
        append(escapeText(model.heading))
        append("</h2><dl><div><dt>Needs attention</dt><dd>")
        append(model.projects.count { it.status == ProjectStatus.AT_RISK })
        append("</dd></div><div><dt>Average progress</dt><dd>")
        append(averageProgress.toInt())
        append("%</dd></div></dl></header>")
        append("<form method=\"get\" action=\"/projects\">")
        append("<label for=\"owned-")
        append(model.instanceKey)
        append("-query\">Filter projects</label><input id=\"owned-")
        append(model.instanceKey)
        append("-query\" name=\"q\" value=\"")
        append(escapeAttribute(model.query))
        append("\"><button type=\"submit\">Apply</button></form>")
        append(renderRows(model))
        append("<p role=\"status\" aria-live=\"polite\">")
        append(model.projects.size)
        append(" projects</p></article>")
    }

    public fun renderRows(model: ProjectBoardModel): String = buildString {
        append("<div data-woge-region=\"owned-project-board.")
        append(model.instanceKey)
        append(".rows\"><table><caption>Projects with owners and delivery progress</caption>")
        append("<thead><tr><th scope=\"col\">Project</th><th scope=\"col\">Owner</th>")
        append("<th scope=\"col\">Status</th><th scope=\"col\">Progress</th><th scope=\"col\">Actions</th></tr></thead><tbody>")
        model.projects.forEach { project ->
            append("<tr><th scope=\"row\"><a href=\"/projects/")
            append(project.id)
            append("\">")
            append(escapeText(project.title))
            append("</a></th><td>")
            append(escapeText(project.owner))
            append("</td><td>")
            append(escapeText(project.status.label))
            append("</td><td><progress max=\"100\" value=\"")
            append(project.progress)
            append("\">")
            append(project.progress)
            append("%</progress></td><td><form method=\"post\" action=\"/projects/")
            append(project.id)
            append("/archive\"><input type=\"hidden\" name=\"csrf\" value=\"")
            append(escapeAttribute(model.csrfToken))
            append("\"><button type=\"submit\">Archive ")
            append(escapeText(project.title))
            append("</button></form></td></tr>")
        }
        append("</tbody></table></div>")
    }
}
