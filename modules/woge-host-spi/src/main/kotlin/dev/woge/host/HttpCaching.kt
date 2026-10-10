package dev.woge.host

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Finalizes ordinary HTTP cache headers after application authorization, before rendering.
 * Pages are no-store unless the application supplies Cache-Control explicitly. Unsafe requests,
 * patch responses, failures and responses setting cookies always remain no-store.
 */
public fun PageResult.forHttpRequest(
    method: RequestMethod,
    ifNoneMatch: List<String> = emptyList(),
    ifModifiedSince: List<String> = emptyList(),
): PageResult {
    val safe = method == RequestMethod.GET || method == RequestMethod.HEAD
    val forceNoStore =
        !safe || this is PageResult.RegionUpdates || metadata.status.isError || metadata.cookies.isNotEmpty()
    val finalized = metadata.cacheMetadata(forceNoStore)
    return when (this) {
        is PageResult.Document ->
            if (safe && finalized.canRevalidate() && matchesValidators(finalized, ifNoneMatch, ifModifiedSince)) {
                PageResult.NotModified(
                    ResponseMetadata(
                        status = ResponseStatus.NOT_MODIFIED,
                        contentType = null,
                        headers = finalized.headers,
                    ),
                )
            } else {
                PageResult.Document(finalized, frames, maxBytes)
            }
        is PageResult.Redirect -> PageResult.Redirect(finalized, location)
        is PageResult.Failure -> PageResult.Failure(finalized, failure)
        is PageResult.NotModified -> PageResult.NotModified(finalized)
        is PageResult.RegionUpdates ->
            PageResult.RegionUpdates(
                patches,
                nativeResult.forHttpRequest(RequestMethod.POST),
                focusSummary,
                finalized,
                patchStreamLimits,
            )
    }
}

private fun ResponseMetadata.cacheMetadata(forceNoStore: Boolean): ResponseMetadata {
    val cacheControl = headers.values(HeaderName.of("Cache-Control"))
    val entries =
        if (forceNoStore || cacheControl.isEmpty()) {
            headers.filter { it.name.value !in CACHE_FIELDS } + httpHeader("Cache-Control", "no-store")
        } else {
            headers.toList()
        }
    return ResponseMetadata(status, contentType, ResponseHeaders.of(entries), cookies)
}

private fun ResponseMetadata.canRevalidate(): Boolean =
    status == ResponseStatus.OK &&
        headers.values(HeaderName.of("Cache-Control")).none {
            it.value.split(',').any { directive -> directive.trim().equals("no-store", ignoreCase = true) }
        }

private fun matchesValidators(
    metadata: ResponseMetadata,
    ifNoneMatch: List<String>,
    ifModifiedSince: List<String>,
): Boolean {
    if (ifNoneMatch.isNotEmpty()) {
        return matchesEntityTag(metadata, ifNoneMatch)
    }
    val since = ifModifiedSince.singleOrNull()?.httpDate()
    val modified =
        metadata.headers
            .values(HeaderName.of("Last-Modified"))
            .singleOrNull()
            ?.value
            ?.httpDate()
    return since != null && modified != null && !modified.isAfter(since)
}

private fun matchesEntityTag(
    metadata: ResponseMetadata,
    conditions: List<String>,
): Boolean {
    val tags = conditions.conditionTags() ?: return false
    val current =
        metadata.headers
            .values(HeaderName.of("ETag"))
            .singleOrNull()
            ?.value
    val entity = current?.let(::parseEntityTags)?.singleOrNull()?.removePrefix("W/")
    return tags == listOf("*") || (entity != null && tags.any { it.removePrefix("W/") == entity })
}

private fun List<String>.conditionTags(): List<String>? =
    if (sumOf { it.length.toLong() } + size - 1 > MAX_CONDITION_BYTES) {
        null
    } else {
        parseEntityTags(joinToString(","))
    }

/** Commas inside quoted opaque tags are data, not list separators. Invalid conditions are ignored. */
private fun parseEntityTags(value: String): List<String>? =
    when {
        value.length > MAX_CONDITION_BYTES -> null
        value.trim() == "*" -> listOf("*")
        else -> quotedEntityTags(value.trim())
    }

private fun quotedEntityTags(value: String): List<String>? {
    val matches = ENTITY_TAG.findAll(value).toList()
    if (matches.isEmpty()) return null
    val complete = matches.first().range.first == 0 && matches.last().range.last == value.lastIndex
    val separated =
        matches.zipWithNext().all { (left, right) ->
            value.substring(left.range.last + 1, right.range.first).trim() == ","
        }
    return matches.map { it.value }.takeIf { complete && separated }
}

private fun String.httpDate(): Instant? {
    if (length > MAX_DATE_BYTES) return null
    val normalized = replace(DATE_SPACES, " ")
    return listOf(DateTimeFormatter.RFC_1123_DATE_TIME, ASCTIME).firstNotNullOfOrNull { format ->
        try {
            ZonedDateTime.parse(normalized, format).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    } ?: normalized.rfc850Date()
}

private fun String.rfc850Date(): Instant? {
    val now = ZonedDateTime.now(ZoneOffset.UTC)
    val formatter =
        DateTimeFormatterBuilder()
            .appendPattern("dd-MMM-")
            .appendValueReduced(ChronoField.YEAR, 2, 2, now.year - RFC850_PAST_YEARS)
            .appendPattern(" HH:mm:ss 'GMT'")
            .toFormatter(Locale.US)
            .withZone(ZoneOffset.UTC)
    return try {
        val parsed = ZonedDateTime.parse(substringAfter(", ", ""), formatter)
        val resolved =
            if (parsed.isAfter(
                    now.plusYears(RFC850_FUTURE_YEARS),
                )
            ) {
                parsed.minusYears(CENTURY_YEARS)
            } else {
                parsed
            }
        resolved.takeIf { it.format(FULL_WEEKDAY) == substringBefore(", ") }?.toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
}

private val CACHE_FIELDS = setOf("cache-control", "etag", "last-modified")
private const val MAX_CONDITION_BYTES = 8192
private val ENTITY_TAG = Regex("(?:W/)?\"[\\x21\\x23-\\x7E\\x80-\\xFF]*\"")
private const val MAX_DATE_BYTES = 64
private const val RFC850_PAST_YEARS = 49
private const val RFC850_FUTURE_YEARS = 50L
private const val CENTURY_YEARS = 100L
private val FULL_WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.US)
private val DATE_SPACES = Regex(" +")
private val ASCTIME = DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss uuuu", Locale.US).withZone(ZoneOffset.UTC)
