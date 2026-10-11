package dev.woge.example.project

import kotlinx.serialization.Serializable

@Serializable
public enum class BoardTheme {
    TAILWIND,
}

internal data class BoardTaskDetails(
    val owner: String = "Team",
    val priority: String = "Normal",
    val status: String = "Open",
)

internal data class BoardTaskRows(
    val titles: List<String>,
    val details: Map<String, BoardTaskDetails> = emptyMap(),
    val input: TaskBoardInput = TaskBoardInput(),
)

internal fun validBoardInput(input: TaskBoardInput): Boolean =
    (input.query?.length ?: 0) <= MAX_BOARD_TEXT &&
        (input.edit?.length ?: 0) <= MAX_BOARD_TEXT &&
        (input.page ?: 1) in 1..MAX_BOARD_PAGE

internal fun validBoardDetails(command: AddBoardTask): Boolean =
    command.edit.length <= MAX_BOARD_TEXT &&
        command.owner.isNotBlank() &&
        command.owner.length <= MAX_OWNER_TEXT &&
        command.priority in listOf("Normal", "Urgent") &&
        command.status in listOf("Open", "Done")

internal fun visibleBoardTitles(
    titles: List<String>,
    input: TaskBoardInput,
): List<String> {
    val filtered = titles.filter { it.contains(input.query.orEmpty(), ignoreCase = true) }
    val ordered =
        when (input.descending) {
            true -> filtered.sortedDescending()
            false -> filtered.sorted()
            null -> filtered.asReversed()
        }
    return ordered.drop(((input.page ?: 1) - 1) * BOARD_PAGE_SIZE).take(BOARD_PAGE_SIZE)
}

internal const val BOARD_PAGE_SIZE = 8

private const val MAX_BOARD_TEXT = 120
private const val MAX_OWNER_TEXT = 60
private const val MAX_BOARD_PAGE = 10000

internal fun AddBoardTask.presentation(edit: String? = null): TaskBoardInput =
    TaskBoardInput(theme = theme, query = query, descending = descending, page = page, edit = edit)
