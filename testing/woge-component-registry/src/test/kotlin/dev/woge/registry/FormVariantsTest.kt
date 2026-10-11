package dev.woge.registry

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
import dev.woge.html.renderHtml
import dev.woge.registry.css.formButton
import dev.woge.registry.css.formCheckbox
import dev.woge.registry.css.formFieldRow
import dev.woge.registry.css.formInput
import dev.woge.registry.css.formLayout
import dev.woge.registry.css.formRadioGroup
import dev.woge.registry.css.formSelect
import dev.woge.registry.css.formSuccess
import dev.woge.registry.css.formSummary
import dev.woge.registry.css.formSwitch
import dev.woge.registry.css.formTextarea
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.woge.registry.tailwind.formButton as tailwindFormButton
import dev.woge.registry.tailwind.formCheckbox as tailwindFormCheckbox
import dev.woge.registry.tailwind.formFieldRow as tailwindFormFieldRow
import dev.woge.registry.tailwind.formInput as tailwindFormInput
import dev.woge.registry.tailwind.formLayout as tailwindFormLayout
import dev.woge.registry.tailwind.formRadioGroup as tailwindFormRadioGroup
import dev.woge.registry.tailwind.formSelect as tailwindFormSelect
import dev.woge.registry.tailwind.formSuccess as tailwindFormSuccess
import dev.woge.registry.tailwind.formSummary as tailwindFormSummary
import dev.woge.registry.tailwind.formSwitch as tailwindFormSwitch
import dev.woge.registry.tailwind.formTextarea as tailwindFormTextarea

class FormVariantsTest {
    private val field = FormDecoder(Command.serializer()).field(Command::title, FormElementId.of("title"))
    private val errors =
        FormErrors(FormValues.of(mapOf("title" to listOf("a"))), listOf(FormError(field, "Invalid <&>")))
    private val css =
        dev.woge.registry.css
            .FormControl(
                field,
                errors,
                "Help",
                dev.woge.registry.css
                    .FormControlState(true),
            )
    private val tailwind =
        dev.woge.registry.tailwind.FormControl(
            field,
            errors,
            "Help",
            dev.woge.registry.tailwind
                .FormControlState(true),
        )
    private val cssChoices =
        listOf(
            dev.woge.registry.css
                .FormChoice("a", "Choice", FormElementId.of("choice-a")),
        )
    private val tailwindChoices =
        listOf(
            dev.woge.registry.tailwind
                .FormChoice("a", "Choice", FormElementId.of("choice-a")),
        )
    private val action =
        object : ActionDescriptor<Command>(ActionId.of("save")) {
            override suspend fun execute(request: PageRequest<Command>): PageResult = htmlPage { text("Saved") }
        }

    @Test
    fun `all form variants differ only in presentation classes`() {
        val pairs =
            listOf(
                renderHtml { formFieldRow(css, "Title") { formInput(css) } } to
                    renderHtml { tailwindFormFieldRow(tailwind, "Title") { tailwindFormInput(tailwind) } },
                renderHtml { formTextarea(css) } to
                    renderHtml { tailwindFormTextarea(tailwind) },
                renderHtml { formCheckbox(css, "Check") } to
                    renderHtml { tailwindFormCheckbox(tailwind, "Check") },
                renderHtml { formRadioGroup(css, "Choose", cssChoices) } to
                    renderHtml { tailwindFormRadioGroup(tailwind, "Choose", tailwindChoices) },
                renderHtml { formSelect(css, cssChoices) } to
                    renderHtml { tailwindFormSelect(tailwind, tailwindChoices) },
                renderHtml { formSwitch(css, "Enable") } to
                    renderHtml { tailwindFormSwitch(tailwind, "Enable") },
                renderHtml {
                    formButton("Save") {
                        attribute("name", "intent")
                        attribute("value", "save")
                    }
                } to
                    renderHtml {
                        tailwindFormButton("Save") {
                            attribute("name", "intent")
                            attribute("value", "save")
                        }
                    },
                renderHtml { formSummary(errors, FormElementId.of("errors"), "Errors") } to
                    renderHtml { tailwindFormSummary(errors, FormElementId.of("errors"), "Errors") },
                renderHtml { formSuccess("Saved") } to
                    renderHtml { tailwindFormSuccess("Saved") },
                renderHtml { formLayout(action, pending = true) { formButton("Save") } } to
                    renderHtml { tailwindFormLayout(action, pending = true) { tailwindFormButton("Save") } },
            )
        pairs.forEach { (plain, utilities) ->
            assertEquals(
                plain.replace(Regex(" class=\"[^\"]*\""), ""),
                utilities.replace(Regex(" class=\"[^\"]*\""), ""),
            )
        }
    }

    @Serializable
    private data class Command(
        val title: String,
    )
}
