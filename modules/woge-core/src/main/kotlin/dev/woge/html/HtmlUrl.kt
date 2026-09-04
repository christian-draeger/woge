package dev.woge.html

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** A URL validated for an HTML URL-bearing attribute. */
public sealed interface HtmlUrl {
    /** The already composed URL; dynamic path/query pieces must already be percent-encoded. */
    public val value: String
}

/** A relative, same-application URL with no authority or scheme. */
@JvmInline
public value class ApplicationUrl internal constructor(
    public override val value: String,
) : HtmlUrl

/** An absolute URL using an explicitly supported non-script scheme. */
@JvmInline
public value class ExternalUrl internal constructor(
    public override val value: String,
) : HtmlUrl

/** An explicitly audited URL-like value that bypasses Woge's scheme allowlist. */
@JvmInline
public value class UnsafeHtmlUrl internal constructor(
    internal val value: String,
)

/**
 * Validates a relative URL such as `/projects/42`, `tasks/7`, `?filter=open`, or `#details`.
 *
 * The value cannot contain a scheme, authority, backslash, whitespace, control character, malformed
 * percent escape, or unpaired UTF-16 surrogate. Encode dynamic path and query pieces before calling.
 */
public fun applicationUrl(value: String): ApplicationUrl {
    requireUrlScalarValue(value, allowAsciiSpace = false)
    require('\\' !in value) { "Application URL must not contain a backslash" }

    val parsed = parseUri(value, "application URL")
    require(!parsed.isAbsolute) { "Application URL must not contain a scheme" }
    require(parsed.rawAuthority == null && !value.startsWith("//")) {
        "Application URL must not contain an authority"
    }
    return ApplicationUrl(value)
}

/**
 * Validates an absolute `http`, `https`, `mailto`, or `tel` URL.
 *
 * HTTP(S) URLs require an authority and cannot contain user-info credentials. Dynamic URL components
 * must already be percent-encoded.
 */
public fun externalUrl(value: String): ExternalUrl {
    requireUrlScalarValue(value, allowAsciiSpace = false)
    require('\\' !in value) { "External URL must not contain a backslash" }

    val parsed = parseUri(value, "external URL")
    require(parsed.isAbsolute) { "External URL must contain a scheme" }
    val scheme = parsed.scheme.lowercase(Locale.ROOT)
    require(scheme in SAFE_EXTERNAL_SCHEMES) {
        "External URL scheme '$scheme' is not supported"
    }
    if (scheme == "http" || scheme == "https") {
        require(!parsed.rawAuthority.isNullOrEmpty()) { "HTTP(S) URL must contain an authority" }
        require(parsed.rawUserInfo == null) { "HTTP(S) URL must not contain user-info credentials" }
    } else {
        require(!parsed.rawSchemeSpecificPart.isNullOrEmpty()) { "$scheme URL must contain a value" }
    }
    return ExternalUrl(value)
}

/**
 * Creates a URL-like value after an explicit application audit.
 *
 * This escape hatch permits active or multi-URL syntax, but still rejects controls, invalid Unicode,
 * and line breaks that make source review unreliable.
 */
@UnsafeWogeHtmlApi
public fun unsafeHtmlUrl(value: String): UnsafeHtmlUrl {
    requireUrlScalarValue(value, allowAsciiSpace = true)
    return UnsafeHtmlUrl(value)
}

private val SAFE_EXTERNAL_SCHEMES: Set<String> = setOf("http", "https", "mailto", "tel")

private fun parseUri(
    value: String,
    kind: String,
): URI =
    try {
        URI(value)
    } catch (exception: URISyntaxException) {
        throw IllegalArgumentException("Invalid $kind: ${exception.reason}", exception)
    }

private fun requireUrlScalarValue(
    value: String,
    allowAsciiSpace: Boolean,
) {
    require(value.isNotEmpty()) { "URL must not be empty" }
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character.isHighSurrogate()) {
            require(index + 1 < value.length && value[index + 1].isLowSurrogate()) {
                "URL must not contain an unpaired UTF-16 surrogate"
            }
            index += 2
            continue
        }

        require(!character.isLowSurrogate()) { "URL must not contain an unpaired UTF-16 surrogate" }
        require(character.code >= C0_CONTROL_END_EXCLUSIVE && character.code != DELETE_CONTROL) {
            "URL must not contain control characters"
        }
        require(!character.isWhitespace() || (allowAsciiSpace && character == ' ')) {
            "URL must not contain whitespace; percent-encode it"
        }
        require(character !in BIDI_CONTROL_CHARACTERS) {
            "URL must not contain bidirectional control characters"
        }
        index += 1
    }
}

private const val C0_CONTROL_END_EXCLUSIVE: Int = 0x20
private const val DELETE_CONTROL: Int = 0x7f

private val BIDI_CONTROL_CHARACTERS: Set<Char> =
    setOf(
        '\u061c',
        '\u200e',
        '\u200f',
        '\u202a',
        '\u202b',
        '\u202c',
        '\u202d',
        '\u202e',
        '\u2066',
        '\u2067',
        '\u2068',
        '\u2069',
    )
