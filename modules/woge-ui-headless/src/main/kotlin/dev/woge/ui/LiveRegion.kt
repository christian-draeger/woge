package dev.woge.ui

import dev.woge.html.Attributes

/** How urgently a screen reader announces changed text. */
public enum class LiveRegion(
    internal val role: String,
) {
    /** Read when the user is idle, for example "Task added". */
    STATUS("status"),

    /** Read immediately, for problems the user must act on, for example "Task was not saved". */
    ALERT("alert"),
}

/**
 * Makes this element announce text that a patch or script puts into it.
 *
 * Render the element empty in the first HTML, then update its text (ADR 0048). Combine it with
 * `region(...)` attributes so a typed action outcome becomes the announcement.
 */
public fun Attributes.liveRegion(kind: LiveRegion = LiveRegion.STATUS) {
    attribute("role", kind.role)
}
