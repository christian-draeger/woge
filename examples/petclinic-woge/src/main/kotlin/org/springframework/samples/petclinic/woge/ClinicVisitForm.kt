package org.springframework.samples.petclinic.woge

import dev.woge.host.FormElementId
import dev.woge.host.RenderSession
import dev.woge.host.WogeRegion
import dev.woge.host.region
import dev.woge.html.ApplicationUrl
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.button
import dev.woge.html.form
import dev.woge.html.h1
import dev.woge.html.h2
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.input
import dev.woge.html.label
import dev.woge.html.li
import dev.woge.html.main
import dev.woge.html.meta
import dev.woge.html.moduleScript
import dev.woge.html.p
import dev.woge.html.section
import dev.woge.html.title
import dev.woge.html.ul
import java.time.LocalDate

internal val visitSummary = FormElementId.of("visit-errors")

internal class ClinicVisitFormView(
    val action: ApplicationUrl,
    val tokenName: String,
    val tokenValue: String,
    val minimumDate: LocalDate,
    val date: String = "",
    val description: String = "",
    val errors: List<VisitError> = emptyList(),
)

internal data class VisitError(val field: String?, val message: String)

internal fun HtmlWriter.clinicVisitDocument(
    heading: String,
    asset: ApplicationUrl,
    render: RenderSession,
    view: ClinicVisitFormView,
) {
    doctype()
    html(attributes = { attribute("lang", "en") }) {
        head {
            meta(attributes = { attribute("charset", "utf-8") })
            render.metadata(this)
            title("$heading · PetClinic")
            moduleScript(asset)
        }
        body {
            main {
                h1 { text(heading) }
                region(render.target(ClinicVisitFormRegion), view)
                p(attributes = {
                    attribute("id", "visit-status")
                    attribute("role", "status")
                }) {}
                p(attributes = {
                    attribute("id", "visit-alert")
                    attribute("role", "alert")
                }) {}
            }
        }
    }
}

@WogeRegion
internal fun HtmlWriter.clinicVisitForm(view: ClinicVisitFormView) {
    if (view.errors.isNotEmpty()) visitErrorSummary(view.errors)
    form(attributes = {
        attribute("method", "post")
        url("action", view.action)
        data("woge-action", "")
        data("woge-status", "visit-status")
        data("woge-alert", "visit-alert")
        data("woge-error-summary", visitSummary.value)
        data("woge-failure-message", "The result could not be confirmed. Check the owner page before submitting again.")
    }) {
        input(attributes = {
            attribute("type", "hidden")
            attribute("name", view.tokenName)
            attribute("value", view.tokenValue)
        })
        visitField("date", "Date", view.date, view.errors, view.minimumDate)
        visitField("description", "Description", view.description, view.errors)
        button(attributes = { data("woge-state-key", "visit-submit") }) { text("Add visit") }
    }
}

private fun HtmlWriter.visitErrorSummary(errors: List<VisitError>) {
    section(attributes = {
        attribute("id", visitSummary.value)
        attribute("tabindex", "-1")
        attribute("aria-label", "Check your visit")
    }) {
        h2 { text("Check your visit") }
        ul {
            for (error in errors) {
                li {
                    val field = error.field
                    if (field == null) {
                        text(error.message)
                    } else {
                        a(attributes = { url("href", applicationUrl("#visit-$field")) }) { text(error.message) }
                    }
                }
            }
        }
    }
}

private fun HtmlWriter.visitField(
    name: String,
    caption: String,
    value: String,
    errors: List<VisitError>,
    minimumDate: LocalDate? = null,
) {
    val id = "visit-$name"
    val messages = errors.filter { it.field == name }
    label(attributes = { attribute("for", id) }) { text(caption) }
    input(attributes = {
        attribute("id", id)
        data("woge-state-key", id)
        attribute("name", name)
        attribute("value", value)
        if (minimumDate == null) {
            attribute("maxlength", "255")
        } else {
            attribute("type", "date")
            attribute("min", minimumDate.toString())
        }
        if (messages.isNotEmpty()) {
            attribute("aria-invalid", "true")
            attribute("aria-describedby", "$id-error")
        }
        boolean("required")
    })
    if (messages.isNotEmpty()) {
        p(attributes = { attribute("id", "$id-error") }) {
            text(messages.joinToString(" ") { it.message })
        }
    }
}
