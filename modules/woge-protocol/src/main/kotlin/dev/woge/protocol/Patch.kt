package dev.woge.protocol

import dev.woge.html.Attributes
import dev.woge.html.BufferedHtmlSink
import dev.woge.html.HtmlByteBudget
import dev.woge.html.HtmlSink
import dev.woge.html.HtmlWriter
import dev.woge.html.streamHtml
import java.nio.charset.StandardCharsets

/** Version of the semantic Woge patch contract. */
@JvmInline
public value class PatchProtocolVersion private constructor(
    public val value: Int,
) {
    public companion object {
        public val CURRENT: PatchProtocolVersion = PatchProtocolVersion(1)

        /** Parses a positive version. Unsupported versions are rejected by concrete patch types. */
        public fun of(value: Int): PatchProtocolVersion {
            require(value > 0) { "Patch protocol version must be positive" }
            return PatchProtocolVersion(value)
        }
    }
}

/** Opaque identifier for one patch, suitable for diagnostics and future deduplication. */
@JvmInline
public value class PatchId private constructor(
    public val value: String,
) {
    public companion object {
        public fun of(value: String): PatchId = PatchId(validateOpaqueId(value, "Patch ID"))
    }
}

/** Opaque identity of one complete browser document. It is not an authorization capability. */
@JvmInline
public value class PageEpoch private constructor(
    public val value: String,
) {
    public companion object {
        /** Parses an already generated epoch; generation and integrity protection are runtime work. */
        public fun of(value: String): PageEpoch = PageEpoch(validateOpaqueId(value, "Page epoch"))
    }
}

/** Opaque generated region target. The value is never interpreted as a CSS selector. */
@JvmInline
public value class RegionTargetId private constructor(
    public val value: String,
) {
    public companion object {
        public fun of(value: String): RegionTargetId = RegionTargetId(validateOpaqueId(value, "Region target ID"))
    }
}

/** Identifies one legal target inside one page epoch. Possession never grants authorization. */
public data class PatchTarget(
    public val pageEpoch: PageEpoch,
    public val region: RegionTargetId,
)

/** Monotonic browser interaction ordering assigned when an enhanced interaction starts. */
@JvmInline
public value class InteractionSequence private constructor(
    public val value: Long,
) {
    public companion object {
        public val INITIAL: InteractionSequence = InteractionSequence(0)

        public fun of(value: Long): InteractionSequence {
            require(value >= 0) { "Interaction sequence must not be negative" }
            return InteractionSequence(value)
        }
    }
}

/** Monotonic revision scoped to one [PatchTarget]. */
@JvmInline
public value class TargetRevision private constructor(
    public val value: Long,
) {
    public companion object {
        public val INITIAL: TargetRevision = TargetRevision(0)

        public fun of(value: Long): TargetRevision {
            require(value >= 0) { "Target revision must not be negative" }
            return TargetRevision(value)
        }
    }
}

/** One exact contiguous revision transition. Gaps and overflow cannot form a valid patch. */
public data class TargetRevisionStep(
    public val base: TargetRevision,
    public val next: TargetRevision,
) {
    init {
        require(base.value < Long.MAX_VALUE && next.value == base.value + 1) {
            "Next target revision must be exactly base revision + 1 without overflow"
        }
    }

    public companion object {
        public fun after(base: TargetRevision): TargetRevisionStep {
            require(base.value < Long.MAX_VALUE) { "Target revision overflow requires a new page epoch" }
            return TargetRevisionStep(base, TargetRevision.of(base.value + 1))
        }
    }
}

/**
 * Materialized HTML owned by one patch.
 *
 * Instances are created by [patchHtml], so ordinary dynamic strings pass through Woge's contextual
 * HTML escaping. Raw markup still requires the explicit unsafe HTML opt-in inside that DSL. The
 * fallback browser sink additionally rejects executable patch content before DOM mutation.
 */
public class PatchHtml internal constructor(
    public val value: String,
) {
    override fun equals(other: Any?): Boolean = other is PatchHtml && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "PatchHtml(length=${value.length}, content=<redacted>)"
}

/** Renders one bounded patch payload through the safe HTML DSL. */
public fun patchHtml(content: HtmlWriter.() -> Unit): PatchHtml {
    val budget = HtmlByteBudget(PatchStreamV1.MAX_PAYLOAD_BYTES.toLong())
    val output = BufferedHtmlSink()
    streamHtml(
        HtmlSink { chunk ->
            budget.consume(chunk.toByteArray(StandardCharsets.UTF_8).size)
            output.write(chunk)
        },
        block = content,
    )
    return PatchHtml(output.content())
}

/** Semantic patch operation. Only operations with implemented behavior are present. */
public enum class PatchOperation {
    REPLACE,
    APPEND,
    REMOVE,
}

/** Stable item identity scoped to the direct children of one collection region. */
@JvmInline
public value class PatchItemId private constructor(
    public val value: String,
) {
    public companion object {
        public fun of(value: String): PatchItemId = PatchItemId(validateOpaqueId(value, "Patch item ID"))
    }
}

/** One typed HTML element owned by a collection item. */
public class PatchItem internal constructor(
    public val id: PatchItemId,
    public val html: PatchHtml,
) {
    override fun toString(): String = "PatchItem(id=${id.value}, html=$html)"
}

/** Writes exactly one item root; the application chooses its normal HTML element and attributes. */
public fun patchItem(
    id: PatchItemId,
    elementName: String = "div",
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit,
): PatchItem =
    PatchItem(
        id,
        patchHtml {
            element(elementName, attributes = {
                attributes()
                attribute("data-woge-item", id.value)
            }, content = content)
        },
    )

/** Appends one identified item; an already present ID retains its existing content. */
public class AppendPatch(
    override val patchId: PatchId,
    override val target: PatchTarget,
    override val interactionSequence: InteractionSequence,
    override val revision: TargetRevisionStep,
    public val item: PatchItem,
) : Patch {
    override val protocolVersion: PatchProtocolVersion = PatchProtocolVersion.CURRENT
    override val operation: PatchOperation = PatchOperation.APPEND

    override fun toString(): String = "AppendPatch(patchId=$patchId, target=$target, revision=$revision, item=$item)"
}

/** Removes one collection item; focus moves to a known region only when the removed item owned it. */
public class RemovePatch(
    override val patchId: PatchId,
    override val target: PatchTarget,
    override val interactionSequence: InteractionSequence,
    override val revision: TargetRevisionStep,
    public val itemId: PatchItemId,
    public val focusTarget: RegionTargetId,
) : Patch {
    override val protocolVersion: PatchProtocolVersion = PatchProtocolVersion.CURRENT
    override val operation: PatchOperation = PatchOperation.REMOVE

    override fun toString(): String =
        "RemovePatch(patchId=$patchId, target=$target, revision=$revision, itemId=$itemId, focusTarget=$focusTarget)"
}

/** Transport-neutral visible update. Framing and native-browser syntax are separate adapters. */
public sealed interface Patch {
    public val protocolVersion: PatchProtocolVersion
    public val operation: PatchOperation
    public val patchId: PatchId
    public val target: PatchTarget
    public val interactionSequence: InteractionSequence
    public val revision: TargetRevisionStep
}

/** Atomically replaces the child content of one known rendered region. */
public class ReplacePatch(
    override val patchId: PatchId,
    override val target: PatchTarget,
    override val interactionSequence: InteractionSequence,
    override val revision: TargetRevisionStep,
    public val html: PatchHtml,
    override val protocolVersion: PatchProtocolVersion = PatchProtocolVersion.CURRENT,
) : Patch {
    override val operation: PatchOperation = PatchOperation.REPLACE

    init {
        require(protocolVersion == PatchProtocolVersion.CURRENT) {
            "Unsupported patch protocol version: ${protocolVersion.value}"
        }
    }

    override fun toString(): String =
        "ReplacePatch(" +
            "protocolVersion=${protocolVersion.value}, " +
            "patchId=${patchId.value}, " +
            "target=$target, " +
            "interactionSequence=${interactionSequence.value}, " +
            "revision=$revision, " +
            "html=$html)"
}

private fun validateOpaqueId(
    value: String,
    label: String,
): String {
    require(value.length in 1..MAX_OPAQUE_ID_LENGTH && value.all(::isOpaqueIdCharacter)) {
        "$label must contain only ASCII letters, digits, '_' or '-'"
    }
    return value
}

private fun isOpaqueIdCharacter(character: Char): Boolean =
    character in 'a'..'z' ||
        character in 'A'..'Z' ||
        character in '0'..'9' ||
        character == '_' ||
        character == '-'

private const val MAX_OPAQUE_ID_LENGTH: Int = 256
