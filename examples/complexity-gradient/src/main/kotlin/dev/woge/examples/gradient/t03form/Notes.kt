package dev.woge.examples.gradient.t03form

import dev.woge.host.FormDecoder
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.host.WogeAction
import dev.woge.host.WogeRoute
import dev.woge.host.actionForm
import dev.woge.host.htmlPage
import dev.woge.host.redirect
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
import dev.woge.html.stylesheet
import dev.woge.html.title
import dev.woge.html.ul
import kotlinx.serialization.Serializable

internal class NoteStore {
    private val notes = mutableListOf("Water the herbs")

    fun all(): List<String> = synchronized(notes) { notes.toList() }

    fun add(text: String) {
        synchronized(notes) { notes += text }
    }
}

internal val noteStore = NoteStore()

@WogeRoute("/notes")
public data object NotesInput

@Serializable
public data class AddNote(
    val text: String,
)

public val addNoteForm: FormDecoder<AddNote> = FormDecoder(AddNote.serializer())

/** Post, redirect, get: the browser reloads the list after the note is stored. */
@WogeAction("add-note")
@Suppress("UnusedParameter")
public suspend fun addNote(
    command: AddNote,
    context: RequestContext,
): PageResult {
    noteStore.add(command.text)
    return redirect(NotesRoute.url(NotesInput))
}

public class NotesPage : PageUseCase<NotesInput> {
    override suspend fun open(request: PageRequest<NotesInput>): PageResult =
        htmlPage {
            doctype()
            html(attributes = { attribute("lang", "en") }) {
                head {
                    meta { attribute("charset", "utf-8") }
                    metadata("viewport", "width=device-width, initial-scale=1")
                    title("Notes · Form")
                    stylesheet(applicationUrl("/assets/form/site.css"))
                }
                body {
                    main {
                        h1 { text("Garden notes") }
                        ul { noteStore.all().forEach { li { text(it) } } }
                        actionForm(AddNoteAction) {
                            label(attributes = { attribute("for", "note-text") }) { text("New note") }
                            input(attributes = {
                                attribute("id", "note-text")
                                attribute("name", "text")
                            })
                            button { text("Add note") }
                        }
                    }
                }
            }
        }
}
