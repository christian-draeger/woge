package dev.woge.ui

import dev.woge.html.form
import dev.woge.html.renderHtml
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UploadProgressTest {
    @Test
    fun `progress is labeled polite and hidden until enhancement starts`() {
        val html =
            renderHtml {
                form(attributes = { enhancedUpload(UiId("upload")) }) {
                    uploadProgress(
                        UiId("upload"),
                        "Uploading <&",
                        "Cancel",
                        UploadMessages("Start", "Cancel", "Fail", "Done"),
                    )
                }
            }
        for (fragment in listOf(
            "data-woge-upload=\"upload\"",
            "id=\"upload\" hidden",
            "for=\"upload-meter\"",
            "Uploading &lt;&amp;",
            "<progress id=\"upload-meter\" max=\"100\">",
            "role=\"status\"",
            "<button type=\"button\">Cancel</button>",
        )) {
            assertTrue(html.contains(fragment), html)
        }
    }
}
