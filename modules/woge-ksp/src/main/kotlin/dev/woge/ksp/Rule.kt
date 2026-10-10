package dev.woge.ksp

/**
 * Every declaration error the processor reports. The ID stays stable so people and tools can search for it.
 *
 * Each message names the rule, the received declaration and the smallest valid alternative.
 */
internal enum class Rule(
    val id: String,
    val rule: String,
    val valid: String,
) {
    REGION_RECEIVER(
        "WOGE-REF-001",
        "A @WogeRegion function must be a top-level extension of dev.woge.html.HtmlWriter.",
        "@WogeRegion fun HtmlWriter.summary(input: Summary) { ... } at the top level of a file",
    ),
    REGION_SHAPE(
        "WOGE-REF-002",
        "A @WogeRegion function takes exactly one input parameter and has no type parameters or suspend modifier.",
        "@WogeRegion fun HtmlWriter.summary(input: Summary) { ... }; put several values into one data class",
    ),
    REGION_VISIBILITY(
        "WOGE-REF-003",
        "A @WogeRegion function, its input type and its component must not be private.",
        "make them internal or public",
    ),
    REGION_COMPONENT(
        "WOGE-REF-004",
        "@WogeRegion(component = ...) must name a class annotated with @WogeComponent.",
        "@WogeComponent class TaskRow(@WogeKey val id: TaskId) and @WogeRegion(component = TaskRow::class)",
    ),
    COMPONENT_KEYS(
        "WOGE-REF-005",
        "A @WogeComponent has at most one @WogeKey constructor parameter.",
        "@WogeComponent class TaskRow(@WogeKey val id: TaskId, val title: String)",
    ),
    COMPONENT_KEY_TYPE(
        "WOGE-REF-006",
        "A @WogeKey must be a non-null String, Long, Int or java.util.UUID, or a value class wrapping one.",
        "@JvmInline value class TaskId(val value: Long) and @WogeKey val id: TaskId",
    ),
    DESCRIPTOR_NAME(
        "WOGE-REF-007",
        "Each @WogeRegion function needs a unique generated descriptor name in its package.",
        "give each region function its own name; summary generates SummaryRegion",
    ),
    IDENTITY_NAME(
        "WOGE-REF-008",
        "Region and component names must use ASCII letters, digits and '_', and stay within 128 characters.",
        "rename the declaration or shorten its package name",
    ),
    ROUTE_PATH(
        "WOGE-ROUTE-001",
        "A @WogeRoute path starts with '/' and has segments that are literal text " +
            "(letters, digits, '-', '.', '_', '~') or one {name} parameter; each name appears once.",
        "@WogeRoute(\"/projects/{project}/tasks\")",
    ),
    ROUTE_TARGET(
        "WOGE-ROUTE-002",
        "@WogeRoute marks an object or a class whose primary constructor has only val properties.",
        "@WogeRoute(\"/projects/{project}\") data class ProjectPageInput(val project: String)",
    ),
    ROUTE_PATH_PARAMETER(
        "WOGE-ROUTE-003",
        "Every {name} in a @WogeRoute path needs a non-null constructor property with the same name.",
        "@WogeRoute(\"/projects/{project}\") data class ProjectPageInput(val project: String)",
    ),
    ROUTE_QUERY_PARAMETER(
        "WOGE-ROUTE-004",
        "A property that is not in the path is a query parameter and must be nullable.",
        "val view: ProjectView? = null",
    ),
    ROUTE_VALUE_TYPE(
        "WOGE-ROUTE-005",
        "Route values are String, Int, Long, Boolean, java.util.UUID, an enum class " +
            "or a value class wrapping one of them.",
        "val project: String, val page: Int? = null or @JvmInline value class TaskId(val value: Long)",
    ),
    ROUTE_VISIBILITY(
        "WOGE-ROUTE-006",
        "A @WogeRoute class, its constructor and its value types must not be private.",
        "make them internal or public",
    ),
    ROUTE_NAME(
        "WOGE-ROUTE-007",
        "Each @WogeRoute class needs a unique generated route name in its package.",
        "rename one class; ProjectPageInput generates ProjectPageRoute",
    ),
    ROUTE_COLLISION(
        "WOGE-ROUTE-008",
        "Two routes in one module must not use the same path pattern; parameter names do not make them different.",
        "change one path, for example /projects/{project} and /archive/{project}",
    ),
    ACTION_SHAPE(
        "WOGE-ACTION-001",
        "@WogeAction marks a top-level suspend function with no receiver or type parameters " +
            "and exactly two parameters.",
        "@WogeAction(\"create-task\") suspend fun createTask(command: CreateTask, context: RequestContext): PageResult",
    ),
    ACTION_ID(
        "WOGE-ACTION-002",
        "An action ID has at most 128 characters, starts with a lowercase ASCII letter " +
            "and uses letters, digits or '-'.",
        "@WogeAction(\"create-task\")",
    ),
    ACTION_COMMAND(
        "WOGE-ACTION-003",
        "The command is a non-null, non-generic data class with val fields using supported form value types. " +
            "Native uploads may wrap that command in MultipartSubmission<Command>.",
        "data class CreateTask(val title: String); use scalar values, enums, value classes or List of scalar values",
    ),
    ACTION_CONTEXT(
        "WOGE-ACTION-004",
        "The second action parameter must be a non-null dev.woge.host.RequestContext.",
        "context: RequestContext; translate host security facts before invocation",
    ),
    ACTION_RETURN(
        "WOGE-ACTION-005",
        "An action returns dev.woge.host.PageResult, not a host response or arbitrary value.",
        ": PageResult = redirect(applicationUrl(\"/tasks\"))",
    ),
    ACTION_VISIBILITY(
        "WOGE-ACTION-006",
        "An action and every referenced command type must be internal or public.",
        "make the action and its command internal or public",
    ),
    ACTION_COLLISION(
        "WOGE-ACTION-007",
        "Action IDs must be unique across the module and generated names must be unique within each package.",
        "give each action a different ID and function name; createTask generates CreateTaskAction",
    ),
    ;

    fun message(received: String): String = "$id $rule\nReceived: $received\nValid: $valid"
}
