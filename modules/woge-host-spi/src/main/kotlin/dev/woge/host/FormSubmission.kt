package dev.woge.host

import dev.woge.html.HtmlWriter
import dev.woge.html.form
import java.util.Collections

/** Request-owned submitted text. Select fields explicitly when rerendering; never echo the whole bag. */
public class FormValues internal constructor(
    values: Map<String, List<String>>,
) {
    private val values =
        Collections.unmodifiableMap(
            values.mapValues { Collections.unmodifiableList(it.value.toList()) },
        )

    public fun first(name: String): String? = values[name]?.firstOrNull()

    public fun all(name: String): List<String> = values[name].orEmpty()

    override fun toString(): String = "FormValues(values=<redacted>)"
}

/** A bounded submission can be a valid command or field errors with text for a native validation page. */
public class FormSubmission<Command : Any> internal constructor(
    public val result: FormResult<Command>,
    public val values: FormValues,
) {
    override fun toString(): String = "FormSubmission(result=$result, values=<redacted>)"
}

public class FormValidation internal constructor(
    public val values: FormValues,
    public val errors: List<FormFieldError>,
) {
    override fun toString(): String = "FormValidation(errors=$errors, values=<redacted>)"
}

/**
 * Renders field errors without executing a mutation. A valid command uses the same action as every
 * other transport; the application still authorizes both the action and its validation page.
 */
public fun <Command : Any> ActionExecutor<Command>.withFormValidation(
    validation: PageUseCase<FormValidation>,
): ActionExecutor<FormSubmission<Command>> =
    ActionExecutor { request ->
        when (val result = request.input.result) {
            is FormResult.Decoded -> execute(PageRequest(result.command, request.context))
            is FormResult.Rejected -> {
                val problem = result.problem
                if (problem is FormProblem.Fields) {
                    validation.open(
                        PageRequest(
                            FormValidation(request.input.values, Collections.unmodifiableList(problem.errors.toList())),
                            request.context,
                        ),
                    )
                } else {
                    failure(FormDecodingException(problem).category, request.context.correlationId)
                }
            }
        }
    }

/** An ordinary native POST form whose endpoint and encoding cannot drift from its descriptor. */
public fun HtmlWriter.actionForm(
    action: ActionDescriptor<*>,
    content: HtmlWriter.() -> Unit,
) {
    form(attributes = {
        attribute("method", "post")
        attribute("enctype", "application/x-www-form-urlencoded")
        attribute("accept-charset", "UTF-8")
        url("action", action.url)
    }, content = content)
}
