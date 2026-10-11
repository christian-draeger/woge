package dev.woge.tck

import dev.woge.host.AuthenticationFacts
import dev.woge.host.DirectoryUploadTemporaryFiles
import dev.woge.host.FailureCategory
import dev.woge.host.FormDecoder
import dev.woge.host.FormDecodingException
import dev.woge.host.FormLocalization
import dev.woge.host.FormResult
import dev.woge.host.MultipartSubmission
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.host.RequestMethod
import dev.woge.host.ResponseStatus
import dev.woge.host.UploadLimits
import dev.woge.host.UploadPolicy
import dev.woge.host.WogeAction
import dev.woge.host.failure
import dev.woge.host.getOrThrow
import dev.woge.host.htmlPage
import dev.woge.host.multipartActionForm
import dev.woge.host.redirect
import dev.woge.html.applicationUrl
import dev.woge.html.button
import dev.woge.html.input
import dev.woge.html.label
import dev.woge.html.script
import dev.woge.ui.UPLOAD_MODULE_PATH
import dev.woge.ui.UiId
import dev.woge.ui.UploadMessages
import dev.woge.ui.enhancedUpload
import dev.woge.ui.uploadProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.net.Socket
import java.net.URI
import java.net.http.HttpTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Serializable
public data class TckUploadCommand(
    public val title: String,
    public val rows: List<TckUploadRow> = emptyList(),
)

@Serializable
public data class TckUploadRow(
    public val key: Int,
    public val title: String,
    public val amount: Double,
    public val date: String,
)

public val tckUploadForm: FormDecoder<TckUploadCommand> =
    FormDecoder(
        TckUploadCommand.serializer(),
        FormLocalization(Locale.GERMANY, "dd.MM.uuuu", setOf("rows[].amount", "rows[].date")),
    )

public fun tckUploadPolicy(directory: Path): UploadPolicy =
    UploadPolicy(
        limits = tckUploadLimits,
        temporaryFiles = { DirectoryUploadTemporaryFiles(directory) },
        authorize = { it.field == "attachment" },
    )

public val tckUploadLimits: UploadLimits = UploadLimits(fileBytes = TCK_UPLOAD_FILE_BYTES)

@WogeAction("tck-upload")
public suspend fun tckUpload(
    submission: MultipartSubmission<TckUploadCommand>,
    context: RequestContext,
): PageResult {
    val principal = (context.authentication as? AuthenticationFacts.Authenticated)?.principal
    if (principal?.subject?.value != "tck-user") {
        return failure(FailureCategory.FORBIDDEN, context.correlationId)
    }
    val result = submission.form.result
    return if (result is FormResult.Rejected) {
        failure(FormDecodingException(result.problem).category, context.correlationId)
    } else {
        val command = submission.form.result.getOrThrow()
        if (command.title == "fail") error("Application failed after upload")
        if (command.rows.isNotEmpty()) {
            check(command.rows == listOf(TckUploadRow(TCK_ROW_KEY, "stable", TCK_ROW_AMOUNT, "2026-10-11")))
        }
        val file = submission.files("attachment").single()
        check(file.read { it.readAllBytes() }.contentEquals(TCK_UPLOAD_CONTENT))
        check(file.size == TCK_UPLOAD_CONTENT.size.toLong())
        check(file.mediaType == "application/octet-stream")
        redirect(applicationUrl("/woge-tck/upload-complete"))
    }
}

public val tckUploadPage: PageUseCase<Unit> =
    PageUseCase {
        htmlPage {
            script(attributes = {
                attribute("type", "module")
                url("src", applicationUrl(UPLOAD_MODULE_PATH))
            })
            multipartActionForm(TckUploadAction, attributes = { enhancedUpload(UiId("upload-progress")) }) {
                label(attributes = { attribute("for", "upload-title") }) { text("Upload title") }
                input(attributes = {
                    attribute("id", "upload-title")
                    attribute("name", "title")
                    attribute("value", "native")
                })
                label(attributes = { attribute("for", "upload-file") }) { text("Attachment") }
                input(attributes = {
                    attribute("id", "upload-file")
                    attribute("name", "attachment")
                    attribute("type", "file")
                })
                button(attributes = { attribute("type", "submit") }) { text("Upload file") }
                uploadProgress(
                    UiId("upload-progress"),
                    "Upload progress",
                    "Cancel upload",
                    UploadMessages("Uploading", "Upload cancelled", "Upload failed; submit again natively", "Uploaded"),
                )
            }
        }
    }

internal suspend fun AdapterTckHttpClient.verifyMultipartUploads(
    origin: URI,
    directory: Path,
    expect: (Boolean, String, String) -> Unit,
) {
    val contract = "native-multipart"
    val headers = mapOf("Content-Type" to "multipart/form-data; boundary=upload", "X-Tck-Subject" to "tck-user")
    val valid = uploadBody("native", "\u0001\u0002\u0003")
    val cases =
        listOf(
            Triple(headers, valid, ResponseStatus.SEE_OTHER.code),
            Triple(headers, valid.replace("name=\"attachment\"", "name=\"restricted\""), ResponseStatus.FORBIDDEN.code),
            Triple(
                headers,
                valid.replace("--upload--", nestedUploadFields() + "--upload--"),
                ResponseStatus.SEE_OTHER.code,
            ),
            Triple(
                headers,
                valid.replace("--upload--", nestedUploadFields().replace("1,5", "1,2,3") + "--upload--"),
                ResponseStatus.BAD_REQUEST.code,
            ),
            Triple(headers + ("X-Tck-Subject" to "other-user"), valid, ResponseStatus.FORBIDDEN.code),
            Triple(headers + ("X-Tck-Unverified" to "true"), "bad multipart", ResponseStatus.FORBIDDEN.code),
            Triple(
                headers,
                uploadBody("native", "x".repeat(TCK_UPLOAD_FILE_BYTES.toInt() + 1)),
                ResponseStatus.PAYLOAD_TOO_LARGE.code,
            ),
            Triple(headers, valid.dropLast(UPLOAD_TRUNCATION_BYTES), ResponseStatus.BAD_REQUEST.code),
            Triple(headers, valid.replace("name=\"title\"", "name=\"unknown\""), ResponseStatus.BAD_REQUEST.code),
            Triple(headers, uploadBody("fail", "\u0001\u0002\u0003"), ResponseStatus.INTERNAL_SERVER_ERROR.code),
        )
    cases.forEachIndexed { index, (requestHeaders, body, status) ->
        val response =
            try {
                open(RequestMethod.POST, TckUploadAction.path, requestHeaders, body)
            } catch (timeout: HttpTimeoutException) {
                throw IllegalStateException(
                    "upload case #$index ($status) timed out; threads:\n${uploadThreadDump()}",
                    timeout,
                )
            }
        response.body().close()
        expect(response.statusCode() == status, contract, "upload security/validation/limit status changed: $status")
        if (status == ResponseStatus.SEE_OTHER.code) {
            expect(response.header("location") == "/woge-tck/upload-complete", contract, "not a native redirect")
        }
        expectUploadsClean(directory)
    }
    verifyUploadDisconnect(origin, directory)
}

private suspend fun verifyUploadDisconnect(
    origin: URI,
    directory: Path,
) {
    val prefix = uploadBody("native", "123").substringBefore("\r\n--upload--")
    Socket(origin.host, origin.port).use { socket ->
        val headers =
            "POST ${TckUploadAction.path} HTTP/1.1\r\nHost: ${origin.host}\r\n" +
                "Content-Type: multipart/form-data; boundary=upload\r\nX-Tck-Subject: tck-user\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n"
        socket.getOutputStream().apply {
            write(headers.toByteArray())
            write("${prefix.toByteArray().size.toString(HEX_RADIX)}\r\n$prefix\r\n".toByteArray())
            flush()
        }
        withTimeout(5.seconds) {
            while (Files.list(directory).use { it.findAny().isEmpty }) delay(1.milliseconds)
        }
    }
    expectUploadsClean(directory)
}

private suspend fun expectUploadsClean(directory: Path) {
    withTimeout(5.seconds) {
        while (Files.list(directory).use { it.findAny().isPresent }) delay(1.milliseconds)
    }
}

private fun uploadBody(
    title: String,
    file: String,
): String =
    "--upload\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\n$title\r\n" +
        "--upload\r\nContent-Disposition: form-data; name=\"attachment\"; filename=\"../../unsafe.txt\"\r\n" +
        "Content-Type: application/octet-stream\r\n\r\n$file\r\n--upload--\r\n"

private fun uploadThreadDump(): String =
    Thread.getAllStackTraces().entries.joinToString("\n") { (thread, frames) ->
        "${thread.name} ${thread.state}: " + frames.take(UPLOAD_DUMP_FRAMES).joinToString(" <- ")
    }

private const val UPLOAD_DUMP_FRAMES = 12
private const val TCK_UPLOAD_FILE_BYTES = 32L
private const val UPLOAD_TRUNCATION_BYTES = 12
private val TCK_UPLOAD_CONTENT = "\u0001\u0002\u0003".toByteArray()
private const val HEX_RADIX = 16

private fun nestedUploadFields(): String =
    listOf("key" to "7", "title" to "stable", "amount" to "1,5", "date" to "11.10.2026").joinToString("") {
        "--upload\r\nContent-Disposition: form-data; name=\"rows[7].${it.first}\"\r\n\r\n${it.second}\r\n"
    }

private const val TCK_ROW_KEY = 7
private const val TCK_ROW_AMOUNT = 1.5
