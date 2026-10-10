package dev.woge.ui

/**
 * The HTML `id` that connects a primitive to its trigger, for example a dialog and the link that
 * opens it. Letters, digits, `-` and `_` only, starting with a letter, so it is also a safe CSS
 * selector and URL fragment.
 */
@JvmInline
public value class UiId(
    public val value: String,
) {
    init {
        require(PATTERN.matches(value)) {
            "UI id '$value' must start with a letter and contain only letters, digits, '-' or '_' (max 64)"
        }
    }

    override fun toString(): String = value

    private companion object {
        val PATTERN = Regex("[A-Za-z][A-Za-z0-9_-]{0,63}")
    }
}
