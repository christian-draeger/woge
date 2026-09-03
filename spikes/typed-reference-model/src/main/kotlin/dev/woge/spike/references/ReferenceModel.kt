package dev.woge.spike.references

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@JvmInline
public value class DescriptorId(public val value: String)

@JvmInline
public value class WogeUrl(public val value: String)

public interface PageRef<P : Any> {
    public val id: DescriptorId

    public fun href(parameters: P): WogeUrl
}

public interface ActionRef<C : Any, R : Any> {
    public val id: DescriptorId

    public fun invoke(command: C): ActionInvocation<C, R> = ActionInvocation(this, command)
}

public class ActionInvocation<C : Any, R : Any> internal constructor(
    public val action: ActionRef<C, R>,
    public val command: C,
)

public interface ComponentRef<C : Any, P : Any> {
    public val id: DescriptorId
}

public interface KeyedComponentRef<C : Any, K : Any, P : Any> : ComponentRef<C, P> {
    public fun canonicalKey(key: K): String

    public fun instance(key: K, props: P): ComponentInstance<C, P> =
        ComponentInstance(this, canonicalKey(key), props)
}

public class ComponentInstance<C : Any, P : Any> internal constructor(
    public val component: ComponentRef<C, P>,
    internal val canonicalKey: String,
    public val props: P,
)

public class RegionSlot<C : Any, R : Any> internal constructor(
    public val id: DescriptorId,
) {
    public fun of(owner: ComponentInstance<C, *>, input: R): RegionInstance<C, R> =
        RegionInstance(owner, this, input)
}

public class RegionInstance<C : Any, R : Any> internal constructor(
    public val owner: ComponentInstance<C, *>,
    public val slot: RegionSlot<C, R>,
    public val input: R,
)

public class ReplaceUpdate<C : Any, R : Any> internal constructor(
    public val target: RegionInstance<C, R>,
    public val model: R,
)

public class UpdateBuilder {
    private val mutableUpdates: MutableList<ReplaceUpdate<*, *>> = mutableListOf()

    public val updates: List<ReplaceUpdate<*, *>>
        get() = mutableUpdates.toList()

    public fun <C : Any, R : Any> replace(target: RegionInstance<C, R>, model: R) {
        mutableUpdates += ReplaceUpdate(target, model)
    }
}

@JvmInline
public value class ProjectSlug(public val value: String)

public object ProjectPage : PageRef<ProjectPage.Parameters> {
    public data class Parameters(
        public val project: ProjectSlug,
        public val status: String? = null,
    )

    override val id: DescriptorId = DescriptorId("page.project.v1")

    override fun href(parameters: Parameters): WogeUrl {
        val project = encode(parameters.project.value)
        val query = parameters.status?.let { "?status=${encode(it)}" }.orEmpty()
        return WogeUrl("/projects/$project$query")
    }
}

@JvmInline
public value class TaskId(public val value: Long)

public data class TaskRowProps(
    public val title: String,
    public val completed: Boolean,
)

public data class TaskStatusView(public val completed: Boolean)

public object TaskRow : KeyedComponentRef<TaskRow.Type, TaskId, TaskRowProps> {
    public sealed interface Type

    override val id: DescriptorId = DescriptorId("component.task-row.v1")
    public val status: RegionSlot<Type, TaskStatusView> =
        RegionSlot(DescriptorId("component.task-row.v1#region.status"))

    override fun canonicalKey(key: TaskId): String = key.value.toString()
}

public data class ProjectCardProps(public val title: String)

public object ProjectCard : KeyedComponentRef<ProjectCard.Type, ProjectSlug, ProjectCardProps> {
    public sealed interface Type

    override val id: DescriptorId = DescriptorId("component.project-card.v1")
    override fun canonicalKey(key: ProjectSlug): String = key.value
}

public data class CreateTaskCommand(public val title: String)
public data class CreateTaskResult(public val id: TaskId)

public object CreateTaskAction : ActionRef<CreateTaskCommand, CreateTaskResult> {
    override val id: DescriptorId = DescriptorId("action.create-task.v1")
}

private fun encode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
