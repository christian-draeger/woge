package dev.woge.ui.headless

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

public enum class ProjectStatus(public val label: String) {
    ACTIVE("Active"),
    AT_RISK("At risk"),
    DONE("Done"),
}

public enum class ProjectBoardDensity(public val attributeValue: String) {
    COMFORTABLE("comfortable"),
    COMPACT("compact"),
    SPACIOUS("spacious"),
}

public data class ProjectItem(
    public val id: Long,
    public val title: String,
    public val owner: String,
    public val status: ProjectStatus,
    public val progress: Int,
) {
    init {
        require(id > 0) { "Project id must be positive" }
        require(progress in 0..100) { "Project progress must be between 0 and 100" }
    }
}

public data class ProjectBoardModel(
    public val instanceKey: String,
    public val heading: String,
    public val query: String,
    public val csrfToken: String,
    public val projects: List<ProjectItem>,
) {
    init {
        require(instanceKey.matches(Regex("[a-z][a-z0-9-]{0,63}"))) {
            "Project board instance key must start with a lowercase letter and contain only lowercase letters, digits, or hyphens"
        }
    }
}

public data class ProjectBoardOptions(
    public val showOwner: Boolean = false,
    public val showProgress: Boolean = false,
    public val density: ProjectBoardDensity = ProjectBoardDensity.COMFORTABLE,
)

public object HeadlessProjectBoard {
    public const val componentId: String = "dev.woge.ui.headless.ProjectBoard"
    public val scope: String = stableScopeId(componentId)

    public fun rowsRegion(instanceKey: String): String = "project-board.$instanceKey.rows"

    public fun render(
        model: ProjectBoardModel,
        options: ProjectBoardOptions = ProjectBoardOptions(),
    ): String = buildString {
        append("<section data-woge-component=\"project-board\" data-woge-scope=\"")
        append(scope)
        append("\" data-density=\"")
        append(options.density.attributeValue)
        append("\" aria-labelledby=\"")
        append(model.headingId)
        append("\">")
        append("<header data-part=\"header\"><h2 id=\"")
        append(model.headingId)
        append("\">")
        append(escapeText(model.heading))
        append("</h2>")
        append(filterForm(model))
        append("</header>")
        append(renderRowsRegion(model, options))
        append("<p role=\"status\" aria-live=\"polite\" data-part=\"status\">")
        append(model.projects.size)
        append(" projects</p></section>")
    }

    public fun renderRowsRegion(
        model: ProjectBoardModel,
        options: ProjectBoardOptions = ProjectBoardOptions(),
    ): String = buildString {
        append("<div data-woge-region=\"")
        append(rowsRegion(model.instanceKey))
        append("\" data-woge-scope=\"")
        append(scope)
        append("\" data-part=\"table-region\"><table><caption>Projects</caption><thead><tr>")
        append("<th scope=\"col\">Project</th>")
        if (options.showOwner) append("<th scope=\"col\">Owner</th>")
        append("<th scope=\"col\">Status</th>")
        if (options.showProgress) append("<th scope=\"col\">Progress</th>")
        append("<th scope=\"col\">Actions</th></tr></thead><tbody>")
        model.projects.forEach { project -> append(projectRow(project, model.csrfToken, options)) }
        append("</tbody></table></div>")
    }

    private fun filterForm(model: ProjectBoardModel): String =
        "<form method=\"get\" action=\"/projects\" data-part=\"filter\">" +
            "<label for=\"${model.queryId}\">Filter projects</label>" +
            "<input id=\"${model.queryId}\" name=\"q\" value=\"${escapeAttribute(model.query)}\">" +
            "<button type=\"submit\">Apply</button></form>"

    private fun projectRow(
        project: ProjectItem,
        csrfToken: String,
        options: ProjectBoardOptions,
    ): String = buildString {
        append("<tr data-project-id=\"")
        append(project.id)
        append("\"><th scope=\"row\"><a href=\"/projects/")
        append(project.id)
        append("\">")
        append(escapeText(project.title))
        append("</a></th>")
        if (options.showOwner) append("<td data-part=\"owner\">${escapeText(project.owner)}</td>")
        append("<td><span data-part=\"status-badge\" data-status=\"")
        append(project.status.name.lowercase())
        append("\">")
        append(escapeText(project.status.label))
        append("</span></td>")
        if (options.showProgress) {
            append("<td><progress max=\"100\" value=\"")
            append(project.progress)
            append("\">")
            append(project.progress)
            append("%</progress></td>")
        }
        append("<td><form method=\"post\" action=\"/projects/")
        append(project.id)
        append("/archive\"><input type=\"hidden\" name=\"csrf\" value=\"")
        append(escapeAttribute(csrfToken))
        append("\"><button type=\"submit\">Archive ")
        append(escapeText(project.title))
        append("</button></form></td></tr>")
    }
}

public fun stableScopeId(componentId: String): String {
    require(componentId.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{2,255}"))) {
        "Component id must be a stable qualified name"
    }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(componentId.toByteArray(StandardCharsets.UTF_8))
        .take(6)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "w-$digest"
}

private val ProjectBoardModel.headingId: String
    get() = "project-board-${instanceKey}-heading"

private val ProjectBoardModel.queryId: String
    get() = "project-board-${instanceKey}-query"

public fun escapeText(value: String): String = buildString(value.length) {
    value.forEach { character ->
        append(
            when (character) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                else -> character
            },
        )
    }
}

public fun escapeAttribute(value: String): String = buildString(value.length) {
    value.forEach { character ->
        append(
            when (character) {
                '&' -> "&amp;"
                '"' -> "&quot;"
                '<' -> "&lt;"
                else -> character
            },
        )
    }
}
