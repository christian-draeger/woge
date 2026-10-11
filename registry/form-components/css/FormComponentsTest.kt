// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.host.ActionDescriptor
import dev.woge.host.ActionId
import dev.woge.host.FormDecoder
import dev.woge.host.FormElementId
import dev.woge.host.FormError
import dev.woge.host.FormErrors
import dev.woge.host.FormValues
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.htmlPage
import dev.woge.html.applicationUrl
import dev.woge.html.div
import dev.woge.html.renderHtml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FormComponentsTest {
    private val decoder = FormDecoder(Command.serializer())
    private val title = decoder.field(Command::title, FormElementId.of("title"), serializedName = "task-title")
    private val body = decoder.field(Command::body, FormElementId.of("body"))
    private val enabled = decoder.field(Command::enabled, FormElementId.of("enabled"))
    private val choice = decoder.field(Command::choice, FormElementId.of("choice"))
    private val choices =
        listOf(
            FormChoice("a", "أول", FormElementId.of("choice-a")),
            FormChoice("b", "Zweite", FormElementId.of("choice-b")),
        )
    private val action =
        object : ActionDescriptor<Command>(ActionId.of("save")) {
            override suspend fun execute(request: PageRequest<Command>): PageResult = htmlPage { text("Saved") }
        }

    @Test
    fun `native server rejection preserves submitted text with linked focusable errors`() {
        val reader = decoder.body()
        reader.accept("task-title=%3Cbad%3E&body=%3C%2Ftextarea%3E%26".toByteArray())
        val submission = decoder.submission(reader)
        val errors = FormErrors(submission.values, listOf(FormError(title, "Choose <another> title")))
        val control = FormControl(title, errors, "Visible help", FormControlState(required = true))
        val html =
            renderHtml {
                formLayout(action) {
                    formSummary(errors, FormElementId.of("errors"), "Check the form")
                    formFieldRow(control, "Title") { formInput(control) }
                    formFieldRow(FormControl(body, errors), "Details") { formTextarea(FormControl(body, errors)) }
                    formButton("Save") {
                        attribute("name", "intent")
                        attribute("value", "save")
                    }
                }
            }
        listOf(
            "method=\"post\"",
            "action=\"/woge-actions/save\"",
            "name=\"task-title\"",
            "for=\"title\"",
            "value=\"&lt;bad&gt;\"",
            "aria-invalid=\"true\"",
            "aria-errormessage=\"title-error\"",
            "aria-describedby=\"title-description title-error\"",
            "id=\"title-error\"",
            " required",
            "href=\"#title\"",
            "id=\"errors\"",
            "tabindex=\"-1\"",
            " autofocus",
            "name=\"intent\"",
            "value=\"save\"",
            "&lt;/textarea&gt;&amp;</textarea>",
            "Choose &lt;another&gt; title",
        ).forEach { assertTrue(html.contains(it), it) }
        assertFalse(html.contains("<script") || html.contains("aria-live") || html.contains("role=\"alert\""))
    }

    @Test
    fun `RTL and translated choices keep native keyboard and screen reader structure`() {
        val errors =
            FormErrors(
                FormValues.of(mapOf("choice" to listOf("b"), "body" to listOf("b"), "enabled" to listOf("true"))),
                listOf(FormError(choice, "Bitte auswählen")),
            )
        val html =
            renderHtml {
                div(attributes = {
                    attribute("dir", "rtl")
                    attribute("lang", "ar")
                }) {
                    formRadioGroup(
                        FormControl(choice, errors, "مساعدة", FormControlState(required = true)),
                        "اختر",
                        choices,
                    )
                    val selected = FormControl(body, errors)
                    formFieldRow(selected, "Details") { formSelect(selected, choices) }
                    formSwitch(FormControl(enabled, errors), "تفعيل")
                }
            }
        listOf(
            "dir=\"rtl\"",
            "lang=\"ar\"",
            "<fieldset",
            "<legend>اختر</legend>",
            "type=\"radio\"",
            "for=\"choice-b\"",
            "id=\"choice-b\"",
            "name=\"choice\"",
            "value=\"b\" checked",
            "aria-describedby=\"choice-description choice-error\"",
            "role=\"switch\"",
            "type=\"checkbox\"",
            "<select",
            "value=\"b\" selected",
            "تفعيل",
        ).forEach { assertTrue(html.contains(it), it) }
        assertFalse(html.contains("aria-checked") || html.contains("role=\"radio\""))
    }

    @Test
    fun `unchecked and multiselect values remain native submitted values`() {
        val errors = FormErrors<Command>(FormValues.of(mapOf("choice" to listOf("a", "b"))), emptyList())
        val html =
            renderHtml {
                formCheckbox(FormControl(enabled, errors), "Enabled")
                formSelect(FormControl(choice, errors), choices, multiple = true)
            }
        assertFalse(html.contains(" checked") || html.contains("type=\"hidden\""))
        assertEquals(2, Regex(" selected").findAll(html).count())
        assertTrue(html.contains(" multiple"))
        val cleared = FormErrors<Command>(FormValues.EMPTY, emptyList())
        assertFalse(renderHtml { formCheckbox(FormControl(enabled, cleared), "Enabled") }.contains(" checked"))
    }

    @Test
    fun `readonly disabled pending and success expose semantic states without rewriting submitters`() {
        val errors = FormErrors<Command>(FormValues.EMPTY, emptyList())
        val html =
            renderHtml {
                formInput(FormControl(title, errors, state = FormControlState(readOnly = true)))
                formTextarea(FormControl(body, errors, state = FormControlState(disabled = true)))
                formLayout(action, pending = true) {
                    formButton("Saving", FormButtonState(pending = true)) {
                        attribute("name", "intent")
                        attribute("value", "save")
                        url("formaction", applicationUrl("/save-draft"))
                    }
                    formButton("Unavailable", FormButtonState(disabled = true))
                    formButton("Cancel", FormButtonState(type = FormButtonType.BUTTON))
                }
                formSuccess("Saved <&>")
            }
        listOf(
            " readonly",
            " disabled",
            "aria-busy=\"true\"",
            "data-pending=\"true\"",
            "name=\"intent\"",
            "value=\"save\"",
            "formaction=\"/save-draft\"",
            "type=\"button\"",
            "role=\"status\"",
            "data-success=\"true\"",
            "Saved &lt;&amp;&gt;",
        ).forEach {
            assertTrue(html.contains(it), it)
        }
        val pending = renderHtml { formButton("Save", FormButtonState(pending = true)) }
        assertFalse(Regex("\\sdisabled(?=[\\s>])").containsMatchIn(pending))
        assertFalse(html.contains("aria-invalid"))
    }

    @Test
    fun `textarea preserves an initial submitted line break through the native parser`() {
        val errors = FormErrors<Command>(FormValues.of(mapOf("body" to listOf("\nDraft <&>"))), emptyList())
        val html = renderHtml { formTextarea(FormControl(body, errors)) }
        assertTrue(html.contains(">\n\nDraft &lt;&amp;&gt;</textarea>"))
    }

    @Test
    fun `empty summary stays absent and native focus can be explicitly opted out`() {
        val empty = FormErrors<Command>(FormValues.EMPTY, emptyList())
        assertEquals("", renderHtml { formSummary(empty, FormElementId.of("errors"), "Errors") })
        val errors = FormErrors(FormValues.EMPTY, listOf(FormError<Command>(null, "Try again")))
        val html = renderHtml { formSummary(errors, FormElementId.of("errors"), "Errors", autofocus = false) }
        assertFalse(html.contains("autofocus") || html.contains("<a "))
        assertTrue(html.contains("<li>Try again</li>"))
    }

    @Test
    fun `unsupported readonly choices and duplicate radio ids fail before markup`() {
        val errors = FormErrors<Command>(FormValues.EMPTY, emptyList())
        val control = FormControl(choice, errors, state = FormControlState(readOnly = true))
        assertThrows(IllegalArgumentException::class.java) { renderHtml { formRadioGroup(control, "Choose", choices) } }
        assertThrows(IllegalArgumentException::class.java) { renderHtml { formSelect(control, choices) } }
        assertThrows(IllegalArgumentException::class.java) { renderHtml { formCheckbox(control, "Choose") } }
        assertThrows(IllegalArgumentException::class.java) {
            renderHtml { formRadioGroup(FormControl(choice, errors), "Choose", listOf(choices[0], choices[0])) }
        }
    }

    @Serializable
    private data class Command(
        @SerialName("task-title") val title: String = "",
        val body: String = "",
        val enabled: Boolean = false,
        val choice: String = "",
    )
}
