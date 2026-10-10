package dev.woge.host

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Incremental URL-encoded UTF-8 reader. A fresh reader belongs to exactly one request.
 * [accept] returns false as soon as input is rejected; callers must stop reading then.
 */
public class FormBody internal constructor(
    internal val limits: FormLimits,
) {
    private val fields = linkedMapOf<String, MutableList<String>>()
    private val part = ByteArrayOutputStream()
    private var name: String? = null
    private var fieldCount = 0
    private var bodyBytes = 0
    private var escape = 0
    private var highNibble = 0
    private var started = false
    private var finished = false
    private var problem: FormProblem? = null

    public fun accept(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size - offset,
    ): Boolean {
        check(!finished) { "Form body has already finished" }
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length) { "Invalid byte range" }
        var index = offset
        while (index < offset + length && problem == null) {
            if (bodyBytes == limits.bodyBytes) {
                reject(FormProblem.LimitExceeded(FormLimit.BODY_BYTES, limits.bodyBytes))
            } else {
                bodyBytes++
                readByte(bytes[index].toInt() and BYTE_MASK)
            }
            index++
        }
        return problem == null
    }

    internal fun finish(): FormFields {
        check(!finished) { "Form body has already finished" }
        finished = true
        if (problem == null) {
            if (escape != 0 ||
                bodyBytes > 0 &&
                !started
            ) {
                reject(FormProblem.MalformedEncoding)
            } else if (started) {
                finishField()
            }
        }
        return FormFields(fields.mapValues { it.value.toList() }, problem)
    }

    private fun readByte(value: Int): Boolean {
        if (escape != 0) return readEscape(value)
        return when (value) {
            '%'.code -> {
                started = true
                escape = 1
                true
            }
            '&'.code -> finishField()
            '='.code -> if (name == null) finishName() else append(value)
            '+'.code -> append(' '.code)
            else -> append(value)
        }
    }

    private fun readEscape(value: Int): Boolean {
        val digit = value.toChar().digitToIntOrNull(HEX_RADIX) ?: return reject(FormProblem.MalformedEncoding)
        return if (escape == 1) {
            highNibble = digit
            escape = 2
            true
        } else {
            escape = 0
            append(highNibble * HEX_RADIX + digit)
        }
    }

    private fun append(value: Int): Boolean {
        started = true
        val threshold = if (name == null) limits.nameBytes else limits.valueBytes
        val limit = if (name == null) FormLimit.NAME_BYTES else FormLimit.VALUE_BYTES
        if (part.size() == threshold) return reject(FormProblem.LimitExceeded(limit, threshold))
        part.write(value)
        return true
    }

    private fun finishName(): Boolean {
        name = text() ?: return false
        return if (name.isNullOrEmpty() ||
            name.orEmpty().any { it.isISOControl() }
        ) {
            reject(FormProblem.MalformedEncoding)
        } else {
            part.reset()
            started = true
            true
        }
    }

    private fun finishField(): Boolean {
        if (fieldCount == limits.fieldCount) {
            return reject(FormProblem.LimitExceeded(FormLimit.FIELD_COUNT, limits.fieldCount))
        }
        return when {
            name == null && !finishName() -> false
            else ->
                text()?.let { value ->
                    fields.getOrPut(checkNotNull(name)) { mutableListOf() }.add(value)
                    fieldCount++
                    part.reset()
                    name = null
                    started = false
                    true
                } ?: false
        }
    }

    private fun text(): String? =
        try {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(part.toByteArray()))
                .toString()
        } catch (_: CharacterCodingException) {
            reject(FormProblem.MalformedEncoding)
            null
        }

    private fun reject(value: FormProblem): Boolean {
        problem = value
        return false
    }
}

internal data class FormFields(
    val values: Map<String, List<String>>,
    val problem: FormProblem?,
)

private const val BYTE_MASK = 0xff
private const val HEX_RADIX = 16
