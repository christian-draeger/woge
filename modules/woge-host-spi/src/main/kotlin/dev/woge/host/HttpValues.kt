package dev.woge.host

import java.util.Collections
import java.util.Locale

/** An HTTP method snapshotted by a host adapter. */
@JvmInline
public value class RequestMethod private constructor(
    public val value: String,
) {
    public companion object {
        public val GET: RequestMethod = RequestMethod("GET")
        public val HEAD: RequestMethod = RequestMethod("HEAD")
        public val POST: RequestMethod = RequestMethod("POST")
        public val PUT: RequestMethod = RequestMethod("PUT")
        public val PATCH: RequestMethod = RequestMethod("PATCH")
        public val DELETE: RequestMethod = RequestMethod("DELETE")
        public val OPTIONS: RequestMethod = RequestMethod("OPTIONS")

        /** Creates a standard or extension HTTP method from a valid token. */
        public fun of(value: String): RequestMethod {
            requireHttpToken(value, "HTTP method")
            return RequestMethod(value)
        }
    }
}

/** A lowercase, syntactically valid HTTP field name. */
@JvmInline
public value class HeaderName private constructor(
    public val value: String,
) {
    public companion object {
        public fun of(value: String): HeaderName {
            requireHttpToken(value, "HTTP header name")
            return HeaderName(value.lowercase(Locale.ROOT))
        }
    }
}

/** An HTTP field value without line breaks or control characters. */
@JvmInline
public value class HeaderValue private constructor(
    public val value: String,
) {
    public companion object {
        public fun of(value: String): HeaderValue {
            require(value.all(::isSafeHeaderCharacter)) {
                "HTTP header value must not contain control characters or line breaks"
            }
            return HeaderValue(value)
        }
    }
}

/** One validated HTTP field. */
public data class HttpHeader(
    public val name: HeaderName,
    public val value: HeaderValue,
) {
    override fun toString(): String = "HttpHeader(name=${name.value}, value=<redacted>)"
}

/** Creates one validated HTTP field. */
public fun httpHeader(
    name: String,
    value: String,
): HttpHeader = HttpHeader(HeaderName.of(name), HeaderValue.of(value))

/** Immutable request-header snapshot owned by Woge rather than a server framework. */
public class RequestHeaders private constructor(
    entries: Iterable<HttpHeader>,
) : Iterable<HttpHeader> {
    private val entries: List<HttpHeader> = immutableList(entries)

    public val size: Int
        get() = entries.size

    public fun values(name: HeaderName): List<HeaderValue> =
        immutableList(
            entries
                .asSequence()
                .filter { it.name == name }
                .map { it.value }
                .asIterable(),
        )

    override fun iterator(): Iterator<HttpHeader> = entries.iterator()

    override fun toString(): String = "RequestHeaders(size=$size)"

    public companion object {
        public val EMPTY: RequestHeaders = RequestHeaders(emptyList())

        public fun of(vararg entries: HttpHeader): RequestHeaders = RequestHeaders(entries.asIterable())

        /** Creates a snapshot without requiring an adapter to copy through a vararg array. */
        public fun of(entries: Iterable<HttpHeader>): RequestHeaders = RequestHeaders(entries)
    }
}

/**
 * Immutable response fields validated for Woge's streaming boundary.
 *
 * Content type, content length, redirect location and cookies have dedicated response types. Host
 * lifecycle fields are adapter-owned, so none of those names can be smuggled through this bag.
 */
public class ResponseHeaders private constructor(
    entries: Iterable<HttpHeader>,
) : Iterable<HttpHeader> {
    private val entries: List<HttpHeader>

    init {
        val snapshot = immutableList(entries)
        val reserved = snapshot.firstOrNull { it.name.value in RESERVED_RESPONSE_HEADERS }
        require(reserved == null) {
            "Response header '${reserved?.name?.value}' is owned by Woge metadata or the host adapter"
        }
        this.entries = snapshot
    }

    public val size: Int
        get() = entries.size

    public fun values(name: HeaderName): List<HeaderValue> =
        immutableList(
            entries
                .asSequence()
                .filter { it.name == name }
                .map { it.value }
                .asIterable(),
        )

    override fun iterator(): Iterator<HttpHeader> = entries.iterator()

    override fun toString(): String = "ResponseHeaders(size=$size)"

    public companion object {
        public val EMPTY: ResponseHeaders = ResponseHeaders(emptyList())

        public fun of(vararg entries: HttpHeader): ResponseHeaders = ResponseHeaders(entries.asIterable())
    }
}

/** A syntactically valid cookie name. */
@JvmInline
public value class CookieName private constructor(
    public val value: String,
) {
    public companion object {
        public fun of(value: String): CookieName {
            requireHttpToken(value, "cookie name")
            return CookieName(value)
        }
    }
}

/** A cookie value that can be serialized without quoting or control characters. */
@JvmInline
public value class CookieValue private constructor(
    public val value: String,
) {
    public companion object {
        public fun of(value: String): CookieValue {
            require(value.all(::isCookieOctet)) {
                "Cookie value contains a character that must be encoded before use"
            }
            return CookieValue(value)
        }
    }
}

/** One parsed request cookie. */
public data class RequestCookie(
    public val name: CookieName,
    public val value: CookieValue,
) {
    override fun toString(): String = "RequestCookie(name=${name.value}, value=<redacted>)"
}

/** Immutable parsed-cookie snapshot. Raw Cookie headers remain adapter-owned. */
public class RequestCookies private constructor(
    entries: Iterable<RequestCookie>,
) : Iterable<RequestCookie> {
    private val entries: List<RequestCookie> = immutableList(entries)

    public val size: Int
        get() = entries.size

    public fun first(name: CookieName): CookieValue? = entries.firstOrNull { it.name == name }?.value

    public fun values(name: CookieName): List<CookieValue> =
        immutableList(
            entries
                .asSequence()
                .filter { it.name == name }
                .map { it.value }
                .asIterable(),
        )

    override fun iterator(): Iterator<RequestCookie> = entries.iterator()

    override fun toString(): String = "RequestCookies(size=$size, values=<redacted>)"

    public companion object {
        public val EMPTY: RequestCookies = RequestCookies(emptyList())

        public fun of(vararg entries: RequestCookie): RequestCookies = RequestCookies(entries.asIterable())

        /** Creates a snapshot without requiring an adapter to copy through a vararg array. */
        public fun of(entries: Iterable<RequestCookie>): RequestCookies = RequestCookies(entries)
    }
}

/** Creates one validated request cookie. */
public fun requestCookie(
    name: String,
    value: String,
): RequestCookie = RequestCookie(CookieName.of(name), CookieValue.of(value))

/** A cookie path. Woge keeps response cookies host-only by not modeling a Domain attribute. */
@JvmInline
public value class CookiePath private constructor(
    public val value: String,
) {
    public companion object {
        public val ROOT: CookiePath = CookiePath("/")

        public fun of(value: String): CookiePath {
            require(value.startsWith('/')) { "Cookie path must start with '/'" }
            require(value.none { it == ';' || it.code < ASCII_SPACE || it.code == DELETE }) {
                "Cookie path must not contain controls or ';'"
            }
            return CookiePath(value)
        }
    }
}

/** Browser SameSite policy for one response cookie. */
public enum class SameSite {
    STRICT,
    LAX,
    NONE,
}

/** A conservative host-only response cookie. */
public data class ResponseCookie(
    public val name: CookieName,
    public val value: CookieValue,
    public val path: CookiePath = CookiePath.ROOT,
    public val secure: Boolean = true,
    public val httpOnly: Boolean = true,
    public val sameSite: SameSite = SameSite.LAX,
    public val maxAgeSeconds: Long? = null,
) {
    init {
        require(maxAgeSeconds == null || maxAgeSeconds >= 0) {
            "Cookie max age must not be negative"
        }
        require(sameSite != SameSite.NONE || secure) {
            "SameSite=None cookies must be Secure"
        }
    }

    override fun toString(): String =
        "ResponseCookie(" +
            "name=${name.value}, " +
            "value=<redacted>, " +
            "path=${path.value}, " +
            "secure=$secure, " +
            "httpOnly=$httpOnly, " +
            "sameSite=$sameSite, " +
            "maxAgeSeconds=$maxAgeSeconds)"
}

/** Creates a conservative Secure, HttpOnly, SameSite=Lax response cookie. */
public fun responseCookie(
    name: String,
    value: String,
): ResponseCookie = ResponseCookie(CookieName.of(name), CookieValue.of(value))

internal fun requireHttpToken(
    value: String,
    label: String,
) {
    require(value.isNotEmpty() && value.all(::isHttpTokenCharacter)) {
        "$label must be a non-empty HTTP token"
    }
}

private fun isHttpTokenCharacter(character: Char): Boolean =
    character in 'a'..'z' ||
        character in 'A'..'Z' ||
        character in '0'..'9' ||
        character in HTTP_TOKEN_PUNCTUATION

private fun isSafeHeaderCharacter(character: Char): Boolean =
    character == '\t' || character.code in ASCII_SPACE..HEADER_CHARACTER_MAX

private fun isCookieOctet(character: Char): Boolean =
    character == '!' ||
        character.code in COOKIE_RANGE_1 ||
        character.code in COOKIE_RANGE_2 ||
        character.code in COOKIE_RANGE_3 ||
        character.code in COOKIE_RANGE_4

private fun <T> immutableList(values: Iterable<T>): List<T> = Collections.unmodifiableList(values.toList())

private const val ASCII_SPACE: Int = 0x20
private const val DELETE: Int = 0x7f
private const val HEADER_CHARACTER_MAX: Int = 0xff
private const val HTTP_TOKEN_PUNCTUATION: String = "!#$%&'*+-.^_`|~"
private const val COOKIE_RANGE_1_START: Int = 0x23
private const val COOKIE_RANGE_1_END: Int = 0x2b
private const val COOKIE_RANGE_2_START: Int = 0x2d
private const val COOKIE_RANGE_2_END: Int = 0x3a
private const val COOKIE_RANGE_3_START: Int = 0x3c
private const val COOKIE_RANGE_3_END: Int = 0x5b
private const val COOKIE_RANGE_4_START: Int = 0x5d
private const val COOKIE_RANGE_4_END: Int = 0x7e
private val COOKIE_RANGE_1: IntRange = COOKIE_RANGE_1_START..COOKIE_RANGE_1_END
private val COOKIE_RANGE_2: IntRange = COOKIE_RANGE_2_START..COOKIE_RANGE_2_END
private val COOKIE_RANGE_3: IntRange = COOKIE_RANGE_3_START..COOKIE_RANGE_3_END
private val COOKIE_RANGE_4: IntRange = COOKIE_RANGE_4_START..COOKIE_RANGE_4_END
private val RESERVED_RESPONSE_HEADERS: Set<String> =
    setOf(
        "connection",
        "content-length",
        "content-type",
        "keep-alive",
        "location",
        "proxy-authenticate",
        "proxy-authorization",
        "set-cookie",
        "te",
        "trailer",
        "transfer-encoding",
        "upgrade",
    )
