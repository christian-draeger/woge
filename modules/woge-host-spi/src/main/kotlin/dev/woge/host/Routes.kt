package dev.woge.host

import dev.woge.html.ApplicationUrl
import dev.woge.html.applicationUrl
import java.net.URLEncoder
import java.util.UUID

/**
 * Marks a page input class as the typed parameters of one URL.
 *
 * Every `{name}` in [path] is a path parameter and needs a non-null property with that name. All
 * other constructor properties are query parameters and must be nullable: an absent or empty query
 * value becomes `null`. The Woge KSP processor generates a [PageRoute] named after the class:
 * `ProjectPageInput` becomes `ProjectPageRoute`, `Search` becomes `SearchRoute`.
 *
 * ```kotlin
 * @WogeRoute("/projects/{project}")
 * data class ProjectPageInput(val project: String, val view: ProjectView? = null)
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class WogeRoute(
    val path: String,
)

/** The decoded values a host adapter extracted from one request that matched a route's path. */
public interface RouteParameters {
    /** The path parameter [name], or `null` if the path has none. */
    public fun path(name: String): String?

    /** The first value of query parameter [name], or `null` if the URL has none. */
    public fun query(name: String): String?
}

/**
 * A request matched a route's path, but a value does not fit the input type.
 *
 * Host adapters answer with [category]: `404 Not Found` for a path value, because the URL names no
 * page, and `400 Bad Request` for a query value. The message never contains the received value.
 */
public class RouteValueException internal constructor(
    public val category: FailureCategory,
    parameter: String,
) : RuntimeException("Route parameter '$parameter' has an invalid value")

/**
 * Generated descriptor of one page URL. Application code uses the generated object, never this type.
 *
 * [url] builds an ordinary link for `href` or a form `action`; [decode] turns a matched request back
 * into the typed input. Host adapters register [path] with their own router.
 */
public abstract class PageRoute<Input : Any>(
    public val path: String,
) {
    init {
        require(RoutePath.isValid(path)) { "Invalid route path '$path'. ${RoutePath.RULE}" }
    }

    /** The link to the page for [input], with every value percent-encoded. */
    public abstract fun url(input: Input): ApplicationUrl

    /** Reads the typed input from a request that matched [path]. Throws [RouteValueException]. */
    public abstract fun decode(parameters: RouteParameters): Input

    /** Fills the path template and appends the query values that are not `null`. */
    protected fun buildUrl(
        path: Map<String, String>,
        query: List<Pair<String, String?>>,
    ): ApplicationUrl {
        val filled =
            RoutePath.PARAMETER.replace(this.path) { match ->
                RouteValues.encode(path.getValue(match.groupValues[1]))
            }
        val present = query.mapNotNull { (name, value) -> value?.let { name to it } }
        val queryString =
            present.joinToString("&", prefix = "?") { (name, value) -> "$name=${RouteValues.encode(value)}" }
        return applicationUrl(if (present.isEmpty()) filled else filled + queryString)
    }

    /** Reads a required path value; a missing or invalid value means the page does not exist. */
    protected fun <T : Any> pathValue(
        parameters: RouteParameters,
        name: String,
        parse: (String) -> T?,
    ): T = parameters.path(name)?.let(parse) ?: throw RouteValueException(FailureCategory.NOT_FOUND, name)

    /** Reads an optional query value; an empty value counts as absent, an invalid one is a bad request. */
    protected fun <T : Any> queryValue(
        parameters: RouteParameters,
        name: String,
        parse: (String) -> T?,
    ): T? {
        val raw = parameters.query(name)?.takeUnless { it.isEmpty() } ?: return null
        return parse(raw) ?: throw RouteValueException(FailureCategory.BAD_REQUEST, name)
    }

    override fun toString(): String = path
}

/**
 * How route values look in a URL. Generated routes use these functions so every host agrees.
 *
 * Numbers and booleans use their Kotlin text, UUIDs their standard form, and enum constants are
 * lowercase with dashes: `IN_PROGRESS` becomes `in-progress`.
 */
public object RouteValues {
    /** The URL form of an enum constant. */
    public fun format(value: Enum<*>): String = value.name.lowercase().replace('_', '-')

    /** The enum constant whose URL form is [value], or `null`. */
    public inline fun <reified E : Enum<E>> enum(value: String): E? =
        enumValues<E>().firstOrNull { format(it) == value }

    /** The UUID in [value], or `null` if it is not a standard UUID. */
    public fun uuid(value: String): UUID? = if (UUID_FORM.matches(value)) UUID.fromString(value) else null

    /** Percent-encodes everything except letters, digits, '-', '.', '_' and '~'. */
    internal fun encode(value: String): String =
        URLEncoder
            .encode(value, Charsets.UTF_8)
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")

    private val UUID_FORM = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
}

/** The route path syntax. The KSP processor checks the same rule during the build. */
internal object RoutePath {
    const val RULE: String =
        "A route path starts with '/' and has segments that are either literal text " +
            "(letters, digits, '-', '.', '_', '~') or one {name} parameter."
    val PARAMETER: Regex = Regex("\\{([A-Za-z][A-Za-z0-9]*)}")
    private val SEGMENT = Regex("[A-Za-z0-9._~-]+|\\{[A-Za-z][A-Za-z0-9]*}")

    fun isValid(path: String): Boolean {
        val segments = path.removePrefix("/").split('/')
        val names = PARAMETER.findAll(path).map { it.groupValues[1] }.toList()
        return path == "/" ||
            path.startsWith("/") &&
            segments.all(SEGMENT::matches) &&
            names.size == names.toSet().size
    }
}
