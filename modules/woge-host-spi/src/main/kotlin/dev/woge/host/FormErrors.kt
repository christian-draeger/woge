package dev.woge.host

import dev.woge.html.Attributes
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.h2
import dev.woge.html.li
import dev.woge.html.p
import dev.woge.html.section
import dev.woge.html.ul
import java.util.Collections

/** A document-owned ID safe for form references and fragment links. */
@JvmInline
public value class FormElementId private constructor(
    public val value: String,
) {
    public companion object {
        public fun of(value: String): FormElementId {
            require(value.length in 1..MAX_FORM_ID_LENGTH && FORM_ID.matches(value)) {
                "Form element IDs must contain ASCII letters, digits, '_' or '-'"
            }
            return FormElementId(value)
        }
    }
}

/** Created from a command property by its decoder; reuse it for the field and its error messages. */
public class FormField<Command : Any> internal constructor(
    public val name: String,
    public val id: FormElementId,
) {
    public val errorId: FormElementId = FormElementId.of("${id.value}-error")
}

/** Application-owned, client-safe text; null [field] means an error for the whole form. */
public class FormError<Command : Any>(
    public val field: FormField<Command>?,
    public val message: String,
) {
    init {
        require(message.isNotBlank()) { "A form error needs a client-safe message" }
    }

    override fun toString(): String = "FormError(field=${field?.name}, message=<redacted>)"
}

/** One immutable presentation model for decoded errors or application business-rule errors. */
public class FormErrors<Command : Any>(
    public val values: FormValues,
    errors: Iterable<FormError<Command>>,
) {
    public val errors: List<FormError<Command>> = Collections.unmodifiableList(errors.toList())

    public fun value(field: FormField<Command>): String? = values.first(field.name)

    public fun messages(field: FormField<Command>): List<String> =
        errors.filter { it.field === field }.map { it.message }

    override fun toString(): String = "FormErrors(errors=${errors.size}, values=<redacted>)"
}

/** Keeps normal input attributes visible. Preserved text is an explicit, separate value attribute. */
public fun <Command : Any> Attributes.formField(
    field: FormField<Command>,
    errors: FormErrors<Command>,
    describedBy: List<FormElementId> = emptyList(),
) {
    attribute("name", field.name)
    attribute("id", field.id.value)
    val invalid = errors.messages(field).isNotEmpty()
    if (invalid) attribute("aria-invalid", "true")
    val descriptions = describedBy + if (invalid) listOf(field.errorId) else emptyList()
    if (descriptions.isNotEmpty()) attribute("aria-describedby", descriptions.joinToString(" ") { it.value })
}

public fun <Command : Any> HtmlWriter.formFieldErrors(
    field: FormField<Command>,
    errors: FormErrors<Command>,
) {
    val messages = errors.messages(field)
    if (messages.isNotEmpty()) {
        p(attributes = { attribute("id", field.errorId.value) }) { text(messages.joinToString(" ")) }
    }
}

/** A focusable summary with ordinary links, deliberately not a live region. */
public fun <Command : Any> HtmlWriter.formErrorSummary(
    errors: FormErrors<Command>,
    id: FormElementId,
    title: String,
) {
    if (errors.errors.isNotEmpty()) {
        section(attributes = {
            attribute("id", id.value)
            attribute("tabindex", "-1")
            attribute("aria-label", title)
        }) {
            h2 { text(title) }
            ul {
                errors.errors.forEach { error ->
                    li {
                        val field = error.field
                        if (field == null) {
                            text(error.message)
                        } else {
                            a(
                                attributes = { url("href", applicationUrl("#${field.id.value}")) },
                            ) { text(error.message) }
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_FORM_ID_LENGTH = 256
private val FORM_ID = Regex("[A-Za-z0-9_-]+")
