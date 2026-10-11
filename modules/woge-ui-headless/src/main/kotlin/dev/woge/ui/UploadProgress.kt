package dev.woge.ui

import dev.woge.html.Attributes
import dev.woge.html.HtmlWriter
import dev.woge.html.button
import dev.woge.html.div
import dev.woge.html.label
import dev.woge.html.p
import dev.woge.html.progress

/** Load only on pages that opt into native multipart progress. Does not change the core client. */
public const val UPLOAD_MODULE_PATH: String = "/assets/woge-ui/upload.js"

/** Add to an ordinary multipart POST form. Its successful response must use POST/redirect/GET. */
public fun Attributes.enhancedUpload(progress: UiId) {
    data("woge-upload", progress.value)
}

/** Client-safe, application-localized messages. Progress announcements are throttled to once per second. */
public data class UploadMessages(
    public val started: String,
    public val cancelled: String,
    public val failed: String,
    public val complete: String,
)

/** Inside the form: labeled native progress, a polite status, and a real cancel button. Hidden without JS. */
public fun HtmlWriter.uploadProgress(
    id: UiId,
    label: String,
    cancelLabel: String,
    messages: UploadMessages,
) {
    div(attributes = {
        attribute("id", id.value)
        boolean("hidden")
        data("upload-start", messages.started)
        data("upload-cancelled", messages.cancelled)
        data("upload-failed", messages.failed)
        data("upload-complete", messages.complete)
    }) {
        label(attributes = { attribute("for", "${id.value}-meter") }) { text(label) }
        progress(attributes = {
            attribute("id", "${id.value}-meter")
            attribute("max", "100")
        }) {}
        p(attributes = { attribute("role", "status") }) {}
        button(attributes = { attribute("type", "button") }) { text(cancelLabel) }
    }
}
