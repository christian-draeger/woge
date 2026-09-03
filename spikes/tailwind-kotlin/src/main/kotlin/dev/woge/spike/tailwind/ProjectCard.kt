package dev.woge.spike.tailwind

public enum class ProjectTone {
    INFO,
    WARNING,
}

public fun renderProjectCard(
    title: String,
    tone: ProjectTone,
    stylesheetPaths: List<String> = listOf("/assets/application.css", "/assets/tailwind.min.css"),
): String {
    val toneClassCandidates = when (tone) {
        ProjectTone.INFO -> "bg-brand-500 text-white"
        ProjectTone.WARNING -> "bg-amber-100 text-amber-950"
    }
    val layoutClassCandidates =
        "project-card grid gap-4 p-card md:grid-cols-2 hover:shadow-lg " +
            "[grid-template-columns:minmax(0,1fr)_auto] aria-busy:opacity-60"
    val classAttribute = listOf(layoutClassCandidates, toneClassCandidates).joinToString(" ")
    val stylesheets = stylesheetPaths.joinToString("") {
        "<link rel=\"stylesheet\" href=\"${escapeHtmlAttribute(it)}\">"
    }

    return "$stylesheets<article class=\"$classAttribute\"><h2 class=\"project-card__title\">${escapeHtmlText(title)}</h2></article>"
}

public fun renderPlainProjectCard(title: String): String =
    "<article><h2>${escapeHtmlText(title)}</h2></article>"

private fun escapeHtmlText(value: String): String = buildString(value.length) {
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

private fun escapeHtmlAttribute(value: String): String = buildString(value.length) {
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
