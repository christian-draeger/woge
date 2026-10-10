package dev.woge.tck

import dev.woge.host.AuthenticationFacts
import dev.woge.host.FailureCategory
import dev.woge.host.FormDecoder
import dev.woge.host.FormDecodingException
import dev.woge.host.FormResult
import dev.woge.host.MultipartSubmission
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.host.RequestMethod
import dev.woge.host.ResponseStatus
import dev.woge.host.UploadLimits
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.net.Socket
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Serializable
public data class TckUploadCommand(
    public val title: String,
)

public val tckUploadForm: FormDecoder<TckUploadCommand> = FormDecoder(TckUploadCommand.serializer())
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
            multipartActionForm(TckUploadAction) {
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
    cases.forEach { (requestHeaders, body, status) ->
        val response = open(RequestMethod.POST, TckUploadAction.path, requestHeaders, body)
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

private const val TCK_UPLOAD_FILE_BYTES = 32L
private const val UPLOAD_TRUNCATION_BYTES = 12
private val TCK_UPLOAD_CONTENT = "\u0001\u0002\u0003".toByteArray()
private const val HEX_RADIX = 16
