package dev.woge.host

internal class MultipartHeaders(
    val name: String,
    val filename: String?,
    val mediaType: String?,
)

internal fun multipartBoundary(contentType: String?): String {
    val parts =
        contentType?.let(::multipartParameters)
            ?: throw UploadDecodingException(FailureCategory.UNSUPPORTED_MEDIA_TYPE)
    if (!parts.first.equals("multipart/form-data", ignoreCase = true)) {
        throw UploadDecodingException(FailureCategory.UNSUPPORTED_MEDIA_TYPE)
    }
    val boundary = parts.second["boundary"] ?: badMultipartHeader()
    if (parts.second.keys.any { it != "boundary" } || !BOUNDARY.matches(boundary)) badMultipartHeader()
    return boundary
}

internal fun multipartHeaders(
    header: String,
    nameBytes: Int,
): MultipartHeaders {
    val headers = linkedMapOf<String, String>()
    for (line in header.split("\r\n")) {
        val separator = line.indexOf(':')
        if (separator <= 0) badMultipartHeader()
        val key = line.substring(0, separator).lowercase()
        val value = line.substring(separator + 1).trim()
        if (key !in setOf("content-disposition", "content-type") ||
            headers.put(key, value) != null ||
            value.any { it.isISOControl() }
        ) {
            badMultipartHeader()
        }
    }
    return multipartDisposition(headers, nameBytes)
}

private fun multipartDisposition(
    headers: Map<String, String>,
    nameBytes: Int,
): MultipartHeaders {
    val disposition = multipartParameters(headers["content-disposition"] ?: badMultipartHeader())
    if (!disposition.first.equals("form-data", ignoreCase = true) ||
        disposition.second.keys.any { it !in setOf("name", "filename") }
    ) {
        badMultipartHeader()
    }
    val name = disposition.second["name"] ?: badMultipartHeader()
    if (name.isEmpty() || name.any { it.isISOControl() }) badMultipartHeader()
    if (name.toByteArray(Charsets.UTF_8).size > nameBytes) {
        throw FormDecodingException(FormProblem.LimitExceeded(FormLimit.NAME_BYTES, nameBytes))
    }
    val mediaType = headers["content-type"]
    if (mediaType != null && !MEDIA_TYPE.matches(mediaType)) badMultipartHeader()
    return MultipartHeaders(name, disposition.second["filename"], mediaType)
}

private fun multipartParameters(value: String): Pair<String, Map<String, String>> {
    if (value.any { it.isISOControl() }) badMultipartHeader()
    val separator = value.indexOf(';')
    val primary = if (separator < 0) value.trim() else value.substring(0, separator).trim()
    val parameters = linkedMapOf<String, String>()
    var index = if (separator < 0) value.length else separator
    while (index < value.length) {
        if (value[index++] != ';') badMultipartHeader()
        val equals = value.indexOf('=', index).takeIf { it >= index } ?: badMultipartHeader()
        val key = value.substring(index, equals).trim().lowercase()
        if (!PARAMETER_NAME.matches(key)) badMultipartHeader()
        index = equals + 1
        while (index < value.length && value[index] == ' ') index++
        val (parsed, end) = multipartParameterValue(value, index)
        if (parameters.put(key, parsed) != null) badMultipartHeader()
        index = end
        while (index < value.length && value[index] == ' ') index++
    }
    return primary to parameters
}

private fun multipartParameterValue(
    value: String,
    start: Int,
): Pair<String, Int> {
    if (start >= value.length) badMultipartHeader()
    if (value[start] != '"') {
        val end = value.indexOf(';', start).takeIf { it >= 0 } ?: value.length
        val token = value.substring(start, end).trim()
        if (!PARAMETER_TOKEN.matches(token)) badMultipartHeader()
        return token to end
    }
    val result = StringBuilder()
    var index = start + 1
    while (index < value.length) {
        when (val char = value[index++]) {
            '"' -> return result.toString() to index
            '\\' -> {
                if (index == value.length) badMultipartHeader()
                result.append(value[index++])
            }
            else -> result.append(char)
        }
    }
    badMultipartHeader()
}

private fun badMultipartHeader(): Nothing = throw UploadDecodingException(FailureCategory.BAD_REQUEST)

private val BOUNDARY = Regex("[0-9A-Za-z'()+_,./:=?-]{1,70}")
private val PARAMETER_NAME = Regex("[a-z][a-z0-9-]*")
private val PARAMETER_TOKEN = Regex("""[^;"\s]+""")
private val MEDIA_TYPE =
    Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+(?:; *charset=UTF-8)?", RegexOption.IGNORE_CASE)
