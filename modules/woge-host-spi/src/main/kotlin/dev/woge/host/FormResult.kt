package dev.woge.host

/** Per-request limits, measured in bytes before UTF-8 text is allocated. */
public data class FormLimits(
    public val bodyBytes: Int = DEFAULT_FORM_BODY_BYTES,
    public val fieldCount: Int = 128,
    public val nameBytes: Int = 256,
    public val valueBytes: Int = DEFAULT_FORM_VALUE_BYTES,
) {
    init {
        require(bodyBytes > 0 && fieldCount > 0 && nameBytes > 0 && valueBytes > 0) {
            "Form limits must be positive"
        }
    }
}

public enum class UnknownFormFields {
    REJECT,
    IGNORE,
}

public enum class FormErrorCode {
    MISSING,
    MALFORMED,
    REPEATED,
    UNKNOWN,
}

/** A serialized field name and error code, never a submitted value or parser exception. */
public data class FormFieldError(
    public val field: String,
    public val code: FormErrorCode,
)

public enum class FormLimit {
    BODY_BYTES,
    FIELD_COUNT,
    NAME_BYTES,
    VALUE_BYTES,
}

/** Safe diagnostics for a rejected request. Values remain private to the request. */
public sealed interface FormProblem {
    public data class Fields(
        public val errors: List<FormFieldError>,
    ) : FormProblem

    public data class LimitExceeded(
        public val limit: FormLimit,
        public val threshold: Int,
    ) : FormProblem

    public data object MalformedEncoding : FormProblem

    public data object UnsupportedContentType : FormProblem
}

public sealed interface FormResult<out Command : Any> {
    public data class Decoded<Command : Any>(
        public val command: Command,
    ) : FormResult<Command> {
        override fun toString(): String = "FormResult.Decoded(command=<redacted>)"
    }

    public data class Rejected(
        public val problem: FormProblem,
    ) : FormResult<Nothing>
}

/** Used by adapter input bindings; manual handlers can instead inspect [FormResult]. */
public class FormDecodingException(
    public val problem: FormProblem,
) : RuntimeException("Form rejected: $problem") {
    public val category: FailureCategory =
        when (problem) {
            is FormProblem.LimitExceeded -> FailureCategory.PAYLOAD_TOO_LARGE
            FormProblem.UnsupportedContentType -> FailureCategory.UNSUPPORTED_MEDIA_TYPE
            else -> FailureCategory.BAD_REQUEST
        }
}

public fun <Command : Any> FormResult<Command>.getOrThrow(): Command =
    when (this) {
        is FormResult.Decoded -> command
        is FormResult.Rejected -> throw FormDecodingException(problem)
    }

private const val DEFAULT_FORM_BODY_BYTES = 65_536
private const val DEFAULT_FORM_VALUE_BYTES = 16_384
