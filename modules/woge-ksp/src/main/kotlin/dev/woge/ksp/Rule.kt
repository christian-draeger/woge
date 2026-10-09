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
    ;

    fun message(received: String): String = "$id $rule\nReceived: $received\nValid: $valid"
}
