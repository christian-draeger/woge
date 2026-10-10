package dev.woge.host

import dev.woge.html.ApplicationUrl
import dev.woge.html.applicationUrl
import java.util.Collections

/** Marks a top-level suspend action with an explicit, stable public ID. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class WogeAction(
    public val id: String,
)

/** A public action name, not a class name or a reflective invocation expression. */
@JvmInline
public value class ActionId private constructor(
    public val value: String,
) {
    public companion object {
        public fun of(value: String): ActionId {
            require(value.length in 1..MAX_ACTION_ID_LENGTH && ACTION_ID.matches(value)) {
                "Action ID must start with a lowercase ASCII letter and use lowercase letters, digits or '-'"
            }
            return ActionId(value)
        }
    }
}

/** Executes a typed command after the host has established authentication and CSRF facts. */
public fun interface ActionExecutor<Command : Any> {
    public suspend fun execute(request: PageRequest<Command>): PageResult
}

/** Generated typed action entry point; its URL is an ordinary native form action. */
public abstract class ActionDescriptor<Command : Any>(
    public val id: ActionId,
) : ActionExecutor<Command> {
    public val path: String = "/woge-actions/${id.value}"
    public val url: ApplicationUrl = applicationUrl(path)
}

/** Explicit allowlist of generated actions; lookup never invokes code or casts command values. */
public class ActionRegistry(
    actions: Iterable<ActionDescriptor<*>>,
) {
    public val actions: List<ActionDescriptor<*>> = Collections.unmodifiableList(actions.toList())
    private val byId = this.actions.associateBy { it.id }

    init {
        require(byId.size == this.actions.size) { "Action registry contains duplicate action IDs" }
    }

    public fun find(id: ActionId): ActionDescriptor<*>? = byId[id]
}

private const val MAX_ACTION_ID_LENGTH = 128
private val ACTION_ID = Regex("[a-z][a-z0-9-]*")
