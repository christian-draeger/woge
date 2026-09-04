package dev.woge.protocol

/** Receives encoded protocol bytes in write order without implying network chunk boundaries. */
public fun interface ByteSink {
    /** Writes all bytes or propagates the downstream failure unchanged. */
    public fun write(bytes: ByteArray)
}

/** Stable reasons why a local version-1 patch stream was rejected. */
public enum class PatchStreamErrorCode {
    INVALID_PREAMBLE,
    UNSUPPORTED_VERSION,
    UNKNOWN_FRAME_KIND,
    INVALID_LENGTH,
    METADATA_TOO_LARGE,
    PAYLOAD_TOO_LARGE,
    INVALID_CONTENT_TYPE,
    INVALID_UTF8,
    INVALID_METADATA,
    ACTIVE_CONTENT,
    INVALID_SEQUENCE,
    TRUNCATED_STREAM,
    MISSING_TERMINAL,
    BYTES_AFTER_TERMINAL,
}

/** Typed local protocol failure with a payload-independent diagnostic message. */
public class PatchStreamException internal constructor(
    public val code: PatchStreamErrorCode,
    message: String,
) : IllegalArgumentException(message)

/** A stable safe error code permitted in a terminal remote failure frame. */
@JvmInline
public value class RemoteFailureCode private constructor(
    public val value: String,
) {
    public companion object {
        public fun of(value: String): RemoteFailureCode {
            require(value.startsWith("WOGE_") && value.length <= MAX_REMOTE_VALUE_LENGTH) {
                "Remote failure code must start with WOGE_ and fit the protocol limit"
            }
            require(value.all { it in 'A'..'Z' || it in '0'..'9' || it == '_' }) {
                "Remote failure code must contain only uppercase ASCII letters, digits or '_'"
            }
            return RemoteFailureCode(value)
        }
    }
}

/** Safe correlation identifier carried to the client without exception or request details. */
@JvmInline
public value class RemoteCorrelationId private constructor(
    public val value: String,
) {
    public companion object {
        public fun of(value: String): RemoteCorrelationId {
            require(value.length in 1..MAX_REMOTE_VALUE_LENGTH && value.all(::isCorrelationCharacter)) {
                "Remote correlation ID contains unsupported characters or exceeds the protocol limit"
            }
            return RemoteCorrelationId(value)
        }
    }
}

/** Client recovery behavior explicitly allowed by a remote terminal failure. */
public enum class RecoveryIntent {
    NONE,
    RELOAD,
}

/** Safe metadata for an application failure after HTTP response commit. */
public data class RemotePatchFailure(
    public val code: RemoteFailureCode,
    public val correlationId: RemoteCorrelationId,
    public val recovery: RecoveryIntent = RecoveryIntent.RELOAD,
)

/** One fully validated event decoded from a version-1 patch stream. */
public sealed interface PatchStreamEvent {
    public data class PatchFrame(
        public val patch: Patch,
    ) : PatchStreamEvent

    public data class Complete(
        public val patchCount: Int,
    ) : PatchStreamEvent

    public data class Error(
        public val failure: RemotePatchFailure,
    ) : PatchStreamEvent
}

/** Incremental decoder whose [feed] calls may use arbitrary transport byte boundaries. */
public interface PatchStreamDecoder {
    /** Returns only complete, fully validated events made available by [bytes]. */
    public fun feed(bytes: ByteArray): List<PatchStreamEvent>

    /** Verifies that the stream ended exactly after one terminal frame. */
    public fun finish()
}

/** Public constants and factories for Woge's version-1 fallback patch stream. */
public object PatchStreamV1 {
    public const val MEDIA_TYPE: String = "application/vnd.woge.patch-stream; version=1"
    public const val MAX_METADATA_BYTES: Int = 64 * 1024
    public const val MAX_PAYLOAD_BYTES: Int = 8 * 1024 * 1024
    public const val MAX_CONTENT_TYPE_BYTES: Int = 255

    /** Creates an incremental encoder writing to [sink]. */
    public fun encoder(sink: ByteSink): PatchStreamEncoder = PatchStreamEncoder(sink)

    /** Creates a fresh incremental decoder. */
    public fun decoder(): PatchStreamDecoder = VersionOnePatchStreamDecoder()

    /** Encodes [patches] followed by one completion frame into a deterministic byte array. */
    public fun encode(patches: Iterable<Patch>): ByteArray = encodePatchStream(patches)
}

private fun isCorrelationCharacter(character: Char): Boolean =
    character in 'a'..'z' ||
        character in 'A'..'Z' ||
        character in '0'..'9' ||
        character == '.' ||
        character == '_' ||
        character == ':' ||
        character == '-'

private const val MAX_REMOTE_VALUE_LENGTH: Int = 128
