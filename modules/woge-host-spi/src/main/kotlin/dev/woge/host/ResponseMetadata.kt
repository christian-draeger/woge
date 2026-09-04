package dev.woge.host

import java.util.Collections
import java.util.Locale

/** A validated HTTP response status. */
@JvmInline
public value class ResponseStatus private constructor(
    public val code: Int,
) {
    public val isRedirect: Boolean
        get() = code in REDIRECT_STATUS_CODES

    public val isError: Boolean
        get() = code in ERROR_STATUS_RANGE

    public val allowsBody: Boolean
        get() = code >= MINIMUM_BODY_STATUS_CODE && code !in BODYLESS_STATUS_CODES

    public companion object {
        public val OK: ResponseStatus = ResponseStatus(200)
        public val BAD_REQUEST: ResponseStatus = ResponseStatus(400)
        public val UNAUTHORIZED: ResponseStatus = ResponseStatus(401)
        public val FORBIDDEN: ResponseStatus = ResponseStatus(403)
        public val NOT_FOUND: ResponseStatus = ResponseStatus(404)
        public val CONFLICT: ResponseStatus = ResponseStatus(409)
        public val TOO_MANY_REQUESTS: ResponseStatus = ResponseStatus(429)
        public val INTERNAL_SERVER_ERROR: ResponseStatus = ResponseStatus(500)
        public val SERVICE_UNAVAILABLE: ResponseStatus = ResponseStatus(503)
        public val SEE_OTHER: ResponseStatus = ResponseStatus(303)
        public val TEMPORARY_REDIRECT: ResponseStatus = ResponseStatus(307)
        public val PERMANENT_REDIRECT: ResponseStatus = ResponseStatus(308)

        public fun of(code: Int): ResponseStatus {
            require(code in VALID_STATUS_RANGE) { "HTTP response status must be between 100 and 599" }
            return ResponseStatus(code)
        }
    }
}

/** A normalized media type without parameters. */
@JvmInline
public value class MediaType private constructor(
    public val value: String,
) {
    public companion object {
        public val HTML: MediaType = MediaType("text/html")

        public fun of(
            type: String,
            subtype: String,
        ): MediaType {
            requireHttpToken(type, "Media type")
            requireHttpToken(subtype, "Media subtype")
            return MediaType("${type.lowercase(Locale.ROOT)}/${subtype.lowercase(Locale.ROOT)}")
        }
    }
}

/** A normalized response charset name. */
@JvmInline
public value class CharsetName private constructor(
    public val value: String,
) {
    public companion object {
        public val UTF_8: CharsetName = CharsetName("UTF-8")

        public fun of(value: String): CharsetName {
            require(value.isNotEmpty() && value.all(::isCharsetCharacter)) {
                "Charset must contain only ASCII letters, digits, '.', '_', or '-'"
            }
            return CharsetName(value.uppercase(Locale.ROOT))
        }
    }
}

/** Explicit media type and optional charset metadata. */
public data class ContentType(
    public val mediaType: MediaType,
    public val charset: CharsetName? = null,
) {
    public val value: String
        get() =
            if (charset == null) {
                mediaType.value
            } else {
                "${mediaType.value}; charset=${charset.value}"
            }

    public companion object {
        public val HTML_UTF_8: ContentType = ContentType(MediaType.HTML, CharsetName.UTF_8)
    }
}

/**
 * Immutable metadata finalized before a response body starts.
 *
 * A null [contentType] means that the outcome has no Woge-produced response body. Response fields
 * and cookies are copied so later mutation by application code cannot change committed metadata.
 */
public class ResponseMetadata(
    public val status: ResponseStatus = ResponseStatus.OK,
    public val contentType: ContentType? = ContentType.HTML_UTF_8,
    public val headers: ResponseHeaders = ResponseHeaders.EMPTY,
    cookies: Iterable<ResponseCookie> = emptyList(),
) {
    public val cookies: List<ResponseCookie> =
        Collections.unmodifiableList(cookies.toList())

    override fun toString(): String =
        "ResponseMetadata(" +
            "status=${status.code}, " +
            "contentType=${contentType?.value}, " +
            "headers=${headers.size}, " +
            "cookies=${cookies.size})"
}

private fun isCharsetCharacter(character: Char): Boolean =
    character in 'a'..'z' ||
        character in 'A'..'Z' ||
        character in '0'..'9' ||
        character == '.' ||
        character == '_' ||
        character == '-'

private const val MINIMUM_STATUS_CODE: Int = 100
private const val MAXIMUM_STATUS_CODE: Int = 599
private const val MINIMUM_BODY_STATUS_CODE: Int = 200
private const val MINIMUM_ERROR_STATUS_CODE: Int = 400
private const val MULTIPLE_CHOICES_STATUS_CODE: Int = 300
private const val MOVED_PERMANENTLY_STATUS_CODE: Int = 301
private const val FOUND_STATUS_CODE: Int = 302
private const val SEE_OTHER_STATUS_CODE: Int = 303
private const val NO_CONTENT_STATUS_CODE: Int = 204
private const val RESET_CONTENT_STATUS_CODE: Int = 205
private const val NOT_MODIFIED_STATUS_CODE: Int = 304
private const val TEMPORARY_REDIRECT_STATUS_CODE: Int = 307
private const val PERMANENT_REDIRECT_STATUS_CODE: Int = 308
private val VALID_STATUS_RANGE: IntRange = MINIMUM_STATUS_CODE..MAXIMUM_STATUS_CODE
private val ERROR_STATUS_RANGE: IntRange = MINIMUM_ERROR_STATUS_CODE..MAXIMUM_STATUS_CODE
private val REDIRECT_STATUS_CODES: Set<Int> =
    setOf(
        MULTIPLE_CHOICES_STATUS_CODE,
        MOVED_PERMANENTLY_STATUS_CODE,
        FOUND_STATUS_CODE,
        SEE_OTHER_STATUS_CODE,
        TEMPORARY_REDIRECT_STATUS_CODE,
        PERMANENT_REDIRECT_STATUS_CODE,
    )
private val BODYLESS_STATUS_CODES: Set<Int> =
    setOf(NO_CONTENT_STATUS_CODE, RESET_CONTENT_STATUS_CODE, NOT_MODIFIED_STATUS_CODE)
