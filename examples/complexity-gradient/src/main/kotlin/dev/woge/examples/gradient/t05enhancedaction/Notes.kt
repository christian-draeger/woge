package dev.woge.examples.gradient.t05enhancedaction

import dev.woge.host.FailureCategory
import dev.woge.host.FormDecoder
import dev.woge.host.PageIdentity
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.RequestContext
import dev.woge.host.WogeAction
import dev.woge.host.WogeRegion
import dev.woge.host.WogeRoute
import dev.woge.host.actionForm
import dev.woge.host.actionRegionUpdates
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.host.region
import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.button
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.input
import dev.woge.html.label
import dev.woge.html.li
import dev.woge.html.main
import dev.woge.html.meta
import dev.woge.html.metadata
import dev.woge.html.moduleScript
import dev.woge.html.p
import dev.woge.html.stylesheet
import dev.woge.html.title
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.TargetRevision
import kotlinx.serialization.Serializable
import java.util.UUID

internal class NoteStore {
    private val notes = mutableListOf("Water the herbs")

    fun all(): List<String> = synchronized(notes) { notes.toList() }

    fun add(text: String) {
        synchronized(notes) { notes += text }
    }
}

internal val noteStore = NoteStore()

/** Region names become opaque IDs with this secret; use one shared secret per deployment. */
private val identitySecret = RenderIdentitySecret.random()

@WogeRoute("/enhanced-notes")
public data object EnhancedNotesInput

/** [epoch] and [revision] are the page's ordering state, echoed back by hidden fields. */
@Serializable
public data class AddNote(
    val text: String,
    val epoch: String,
    val revision: Long,
)

public val addNoteForm: FormDecoder<AddNote> = FormDecoder(AddNote.serializer())

internal data class NoteStatus(
    val message: String,
    val epoch: String,
    val revision: Long,
)

/** One implementation: native posts get a 303 redirect, enhanced posts get the two region updates. */
@WogeAction("add-enhanced-note")
public suspend fun addEnhancedNote(
    command: AddNote,
    context: RequestContext,
): PageResult {
    val page =
        try {
            PageIdentity(PageEpoch.of(command.epoch), identitySecret) to TargetRevision.of(command.revision)
        } catch (_: IllegalArgumentException) {
            null
        }
    if (page == null || command.text.isBlank()) return failure(FailureCategory.BAD_REQUEST, context.correlationId)

    noteStore.add(command.text.trim())
    val (identity, revision) = page
    return actionRegionUpdates(EnhancedNotesRoute.url(EnhancedNotesInput)) {
        replace(NoteListRegion.target(identity), noteStore.all(), revision)
        replace(
            NoteStatusRegion.target(identity),
            NoteStatus("Note added", command.epoch, command.revision + 1),
            revision,
        )
    }
}

public class NotesPage : PageUseCase<EnhancedNotesInput> {
    override suspend fun open(request: PageRequest<EnhancedNotesInput>): PageResult {
        val epoch = "notes-${UUID.randomUUID()}"
        val page = PageIdentity(PageEpoch.of(epoch), identitySecret)
        return htmlPage {
            doctype()
            html(attributes = { attribute("lang", "en") }) {
                head {
                    meta { attribute("charset", "utf-8") }
                    metadata("viewport", "width=device-width, initial-scale=1")
                    metadata("woge-page-epoch", epoch)
                    title("Notes · Enhanced action")
                    stylesheet(applicationUrl("/assets/enhanced-action/site.css"))
                    moduleScript(applicationUrl("/assets/enhanced-action/app.js"))
                }
                body {
                    main {
                        h1 { text("Garden notes") }
                        region(NoteListRegion.target(page), noteStore.all(), elementName = "ul")
                        region(NoteStatusRegion.target(page), NoteStatus("", epoch, 0), attributes = {
                            attribute("id", "note-status")
                            attribute("role", "status")
                        })
                        noteForm()
                    }
                }
            }
        }
    }
}

private fun HtmlWriter.noteForm() {
    actionForm(AddEnhancedNoteAction, attributes = {
        attribute("id", "note-form")
        data("woge-action", "")
        data("woge-status", "note-status")
        data("woge-failure-message", "The note was not added. Reload the page and try again.")
    }) {
        label(attributes = { attribute("for", "note-text") }) { text("New note") }
        input(attributes = {
            attribute("id", "note-text")
            attribute("name", "text")
            boolean("required")
        })
        button { text("Add note") }
    }
}

@WogeRegion
internal fun HtmlWriter.noteList(notes: List<String>) {
    notes.forEach { li { text(it) } }
}

@WogeRegion
internal fun HtmlWriter.noteStatus(status: NoteStatus) {
    p { text(status.message) }
    hidden("epoch", status.epoch)
    hidden("revision", status.revision.toString())
}

private fun HtmlWriter.hidden(
    name: String,
    value: String,
) {
    input(attributes = {
        attribute("type", "hidden")
        attribute("name", name)
        attribute("value", value)
        attribute("form", "note-form")
    })
}
