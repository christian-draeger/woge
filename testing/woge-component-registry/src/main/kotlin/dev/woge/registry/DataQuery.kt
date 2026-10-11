package dev.woge.registry

import dev.woge.host.WogeRoute
import dev.woge.html.HtmlWriter
import dev.woge.registry.css.PageWindow

public enum class PeopleSort { NAME, ROLE }

public enum class PeopleOrder { ASC, DESC }

/** The whole state of the people page lives in its URL, so sort, filter and page work as plain links. */
@WogeRoute("/data/{variant}")
public data class PeopleInput(
    val variant: String,
    val q: String? = null,
    val sort: PeopleSort? = null,
    val dir: PeopleOrder? = null,
    val page: Int? = null,
)

internal class Person(
    val id: String,
    val name: String,
    val role: String,
)

internal val PEOPLE =
    listOf(
        Person("ada", "Ada Lovelace", "Engineer"),
        Person("grace", "Grace Hopper", "Admiral"),
        Person("alan", "Alan Turing", "Mathematician"),
        Person("katherine", "Katherine Johnson", "Mathematician"),
        Person("linus", "Linus Torvalds", "Engineer"),
    )

internal const val PEOPLE_PAGE_SIZE = 3

/** What a page shows: one window of the filtered, sorted people, plus the query that produced it. */
internal class PeopleView(
    val input: PeopleInput,
    val rows: List<Person>,
    val window: PageWindow,
    val state: String = "ready",
    val pendingId: String? = null,
) {
    val sort: PeopleSort get() = input.sort ?: PeopleSort.NAME
    val dir: PeopleOrder get() = input.dir ?: PeopleOrder.ASC
}

/** Filtering, sorting and slicing happen on the server; only one window of rows reaches the browser. */
internal fun peopleView(
    input: PeopleInput,
    state: String = "ready",
    pendingId: String? = null,
): PeopleView {
    val matches = PEOPLE.filter { input.q.isNullOrBlank() || it.name.contains(input.q, ignoreCase = true) }
    val key: (Person) -> String = if ((input.sort ?: PeopleSort.NAME) == PeopleSort.NAME) Person::name else Person::role
    val sorted = matches.sortedBy(key).let { if (input.dir == PeopleOrder.DESC) it.reversed() else it }
    val window = PageWindow.of(input.page, PEOPLE_PAGE_SIZE, sorted.size.toLong())
    return PeopleView(input, sorted.drop(window.offset.toInt()).take(window.size), window, state, pendingId)
}

/** One implementation per style variant; both must produce the same markup apart from classes. */
internal interface DataKit {
    val styles: List<String>

    fun HtmlWriter.dashboard(view: PeopleView)

    fun HtmlWriter.panel(view: PeopleView)

    fun HtmlWriter.complexForm()

    fun HtmlWriter.cards()
}

internal fun notice(state: String): String? =
    when (state) {
        "loading" -> "Loading people…"
        "stale" -> "These results may be out of date."
        "partial-error" -> "Roles could not be loaded."
        "optimistic" -> "Saving your change…"
        else -> null
    }
