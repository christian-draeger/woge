package dev.woge.tck

import dev.woge.host.FormDecoder
import dev.woge.host.FormLimits
import dev.woge.host.FormProblem
import dev.woge.host.FormResult
import dev.woge.host.FormSubmission
import dev.woge.host.UnknownFormFields
import dev.woge.host.getOrThrow

/** Host-owned Security fixtures explicitly allow the verified token alongside command fields. */
public val tckSecurityForm: FormDecoder<TckActionCommand> =
    FormDecoder(
        TckActionCommand.serializer(),
        FormLimits(bodyBytes = 512, fieldCount = 4, nameBytes = 16, valueBytes = 128),
        unknownFields = UnknownFormFields.IGNORE,
    )

/** Request-owned parsed input, consumed by an action only after the real Security filter chain. */
public class PreparedSecurityForm(
    public val submission: FormSubmission<TckActionCommand>,
) {
    init {
        val result = submission.result
        if (result is FormResult.Rejected && result.problem !is FormProblem.Fields) result.getOrThrow()
    }

    public val csrfToken: String? get() = submission.values.all("_csrf").singleOrNull()
}

public const val PREPARED_SECURITY_FORM_ATTRIBUTE: String = "dev.woge.tck.preparedSecurityForm"
