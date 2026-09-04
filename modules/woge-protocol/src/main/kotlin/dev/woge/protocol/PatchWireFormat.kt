package dev.woge.protocol

import dev.woge.protocol.internal.protocolFailure
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal fun validateFrameLengths(
    contentTypeLength: Int,
    metadataLength: Int,
    payloadLength: Int,
) {
    if (contentTypeLength !in 1..PatchStreamV1.MAX_CONTENT_TYPE_BYTES) {
        protocolFailure(PatchStreamErrorCode.INVALID_CONTENT_TYPE, "Frame content type length is invalid")
    }
    if (metadataLength > PatchStreamV1.MAX_METADATA_BYTES) {
        protocolFailure(PatchStreamErrorCode.METADATA_TOO_LARGE, "Frame metadata exceeds the version-1 limit")
    }
    if (payloadLength > PatchStreamV1.MAX_PAYLOAD_BYTES) {
        protocolFailure(PatchStreamErrorCode.PAYLOAD_TOO_LARGE, "Frame payload exceeds the version-1 limit")
    }
}

internal fun validateDeclaredLengths(
    contentTypeLength: Int,
    metadataLength: Long,
    payloadLength: Long,
) {
    if (contentTypeLength !in 1..PatchStreamV1.MAX_CONTENT_TYPE_BYTES) {
        protocolFailure(PatchStreamErrorCode.INVALID_CONTENT_TYPE, "Frame content type length is invalid")
    }
    if (metadataLength > PatchStreamV1.MAX_METADATA_BYTES) {
        protocolFailure(PatchStreamErrorCode.METADATA_TOO_LARGE, "Frame metadata exceeds the version-1 limit")
    }
    if (payloadLength > PatchStreamV1.MAX_PAYLOAD_BYTES) {
        protocolFailure(PatchStreamErrorCode.PAYLOAD_TOO_LARGE, "Frame payload exceeds the version-1 limit")
    }
}

internal fun requireContentType(
    kind: FrameKind,
    contentType: String,
) {
    val expected =
        when (kind) {
            FrameKind.PATCH -> PATCH_CONTENT_TYPE
            FrameKind.COMPLETE -> COMPLETE_CONTENT_TYPE
            FrameKind.ERROR -> ERROR_CONTENT_TYPE
        }
    if (contentType != expected) {
        protocolFailure(PatchStreamErrorCode.INVALID_CONTENT_TYPE, "Frame content type is invalid")
    }
}

internal fun requireEmptyTerminalPayload(payloadLength: Int) {
    if (payloadLength != 0) {
        protocolFailure(PatchStreamErrorCode.INVALID_LENGTH, "Terminal frame payload must be empty")
    }
}

internal fun frameHeader(
    kind: FrameKind,
    contentTypeLength: Int,
    metadataLength: Int,
    payloadLength: Int,
): ByteArray =
    ByteBuffer
        .allocate(FRAME_HEADER_BYTES)
        .put(kind.code.toByte())
        .put(contentTypeLength.toByte())
        .putInt(metadataLength)
        .putInt(payloadLength)
        .array()

internal fun String.encodeUtf8(): ByteArray =
    try {
        StandardCharsets.UTF_8
            .newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(this))
            .let { encoded -> ByteArray(encoded.remaining()).also(encoded::get) }
    } catch (_: CharacterCodingException) {
        protocolFailure(PatchStreamErrorCode.INVALID_UTF8, "Patch text cannot be encoded as UTF-8")
    }

internal fun ByteArray.decodeUtf8(): String =
    try {
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(this))
            .toString()
    } catch (_: CharacterCodingException) {
        protocolFailure(PatchStreamErrorCode.INVALID_UTF8, "Patch frame contains invalid UTF-8")
    }

internal fun ByteArray.decodeContentType(): String {
    if (any { byte -> byte.toInt() !in PRINTABLE_ASCII_RANGE }) {
        protocolFailure(PatchStreamErrorCode.INVALID_CONTENT_TYPE, "Frame content type must be printable ASCII")
    }
    return toString(StandardCharsets.US_ASCII)
}

internal fun readUnsignedInt(
    bytes: ByteArray,
    offset: Int,
): Long =
    ((bytes[offset].toLong() and UNSIGNED_BYTE_MASK_LONG) shl BITS_24) or
        ((bytes[offset + FIRST_BYTE_OFFSET].toLong() and UNSIGNED_BYTE_MASK_LONG) shl BITS_16) or
        ((bytes[offset + SECOND_BYTE_OFFSET].toLong() and UNSIGNED_BYTE_MASK_LONG) shl BITS_8) or
        (bytes[offset + THIRD_BYTE_OFFSET].toLong() and UNSIGNED_BYTE_MASK_LONG)

internal fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }

internal enum class FrameKind(
    val code: Int,
    val terminal: Boolean,
) {
    PATCH(PATCH_FRAME_KIND_CODE, false),
    COMPLETE(COMPLETE_FRAME_KIND_CODE, true),
    ERROR(ERROR_FRAME_KIND_CODE, true),
    ;

    companion object {
        fun fromCode(code: Int): FrameKind =
            entries.firstOrNull { it.code == code }
                ?: protocolFailure(PatchStreamErrorCode.UNKNOWN_FRAME_KIND, "Patch frame kind is unknown")
    }
}

internal const val VERSION_ONE: Int = 1
internal const val FRAME_HEADER_BYTES: Int = 10
internal const val METADATA_LENGTH_OFFSET: Int = 2
internal const val PAYLOAD_LENGTH_OFFSET: Int = 6
internal const val PREAMBLE_VERSION_OFFSET: Int = 4
internal const val UNSIGNED_BYTE_MASK: Int = 0xff
private const val UNSIGNED_BYTE_MASK_LONG: Long = 0xff
private const val BITS_8: Int = 8
private const val BITS_16: Int = 16
private const val BITS_24: Int = 24
private const val FIRST_BYTE_OFFSET: Int = 1
private const val SECOND_BYTE_OFFSET: Int = 2
private const val THIRD_BYTE_OFFSET: Int = 3
private const val PATCH_FRAME_KIND_CODE: Int = 1
private const val COMPLETE_FRAME_KIND_CODE: Int = 2
private const val ERROR_FRAME_KIND_CODE: Int = 3
private const val PRINTABLE_ASCII_START: Int = 0x20
private const val PRINTABLE_ASCII_END: Int = 0x7e
private val PRINTABLE_ASCII_RANGE: IntRange = PRINTABLE_ASCII_START..PRINTABLE_ASCII_END
internal val PREAMBLE_MAGIC: ByteArray =
    byteArrayOf(
        'W'.code.toByte(),
        'O'.code.toByte(),
        'G'.code.toByte(),
        'E'.code.toByte(),
    )
internal val PREAMBLE: ByteArray = PREAMBLE_MAGIC + byteArrayOf(VERSION_ONE.toByte())
internal val EMPTY_BYTES: ByteArray = byteArrayOf()
internal const val PATCH_CONTENT_TYPE: String = "text/html; charset=utf-8"
internal const val COMPLETE_CONTENT_TYPE: String = "application/json; charset=utf-8"
internal const val ERROR_CONTENT_TYPE: String = "application/problem+json; charset=utf-8"
internal const val MAX_BUFFER_BYTES: Int =
    FRAME_HEADER_BYTES +
        PatchStreamV1.MAX_CONTENT_TYPE_BYTES +
        PatchStreamV1.MAX_METADATA_BYTES +
        PatchStreamV1.MAX_PAYLOAD_BYTES
