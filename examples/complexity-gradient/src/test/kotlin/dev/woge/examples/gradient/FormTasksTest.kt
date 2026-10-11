package dev.woge.examples.gradient

import dev.woge.examples.gradient.t01staticpage.StaticPage
import dev.woge.examples.gradient.t01staticpage.StaticPageInput
import dev.woge.examples.gradient.t02component.ProjectList
import dev.woge.examples.gradient.t02component.ProjectListInput
import dev.woge.examples.gradient.t03form.AddNoteAction
import dev.woge.examples.gradient.t03form.NotesInput
import dev.woge.examples.gradient.t03form.NotesPage
import dev.woge.examples.gradient.t04validation.ValidatedNotesInput
import dev.woge.examples.gradient.t04validation.addValidatedNote
import dev.woge.examples.gradient.t05enhancedaction.AddEnhancedNoteAction
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.ResponseStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.woge.examples.gradient.t03form.AddNote as FormNote
import dev.woge.examples.gradient.t04validation.AddNote as ValidatedNote
import dev.woge.examples.gradient.t04validation.NotesPage as ValidatedNotesPage
import dev.woge.examples.gradient.t05enhancedaction.AddNote as EnhancedNote

class FormTasksTest {
    @Test
    fun `static page renders heading and stylesheet`() =
        runTest {
            val html = StaticPage().open(PageRequest(StaticPageInput, getContext)).html()
            assertTrue(html.startsWith("<!doctype html>"))
            assertTrue(html.contains("<h1>"))
            assertTrue(html.contains("""rel="stylesheet""""))
        }

    @Test
    fun `component list renders one card per project`() =
        runTest {
            val html = ProjectList().open(PageRequest(ProjectListInput, getContext)).html()
            assertTrue(html.contains("<article"))
        }

    @Test
    fun `form action redirects with 303 and stores the note`() =
        runTest {
            val result = AddNoteAction.execute(PageRequest(FormNote("Prune roses"), postContext))
            val redirect = assertInstanceOf(PageResult.Redirect::class.java, result)
            assertEquals(ResponseStatus.SEE_OTHER, redirect.metadata.status)
            val page = NotesPage().open(PageRequest(NotesInput, getContext)).html()
            assertTrue(page.contains("Prune roses"))
            assertTrue(page.contains("<form"))
        }

    @Test
    fun `blank and long notes return 400 and are not stored`() =
        runTest {
            for (text in listOf("  ", "x".repeat(81))) {
                val result = addValidatedNote(ValidatedNote(text), postContext)
                assertEquals(ResponseStatus.BAD_REQUEST, result.metadata.status)
                assertTrue(result.html().contains("role=\"alert\"") || result.html().contains("aria-invalid"))
            }
            val page = ValidatedNotesPage().open(PageRequest(ValidatedNotesInput, getContext)).html()
            assertTrue(!page.contains("xxxx"))
        }

    @Test
    fun `enhanced action has one executor with native redirect and region updates`() =
        runTest {
            val page =
                dev.woge.examples.gradient.t05enhancedaction
                    .NotesPage()
                    .open(PageRequest(dev.woge.examples.gradient.t05enhancedaction.EnhancedNotesInput, getContext))
                    .html()
            val epoch = Regex("""name="epoch" value="([^"]+)"""").find(page)!!.groupValues[1]
            val result = AddEnhancedNoteAction.execute(PageRequest(EnhancedNote("Mulch", epoch, 0), postContext))
            val updates = assertInstanceOf(PageResult.RegionUpdates::class.java, result)
            assertEquals(2, updates.patches.size)
            assertEquals(ResponseStatus.SEE_OTHER, updates.nativeResult.metadata.status)
            assertTrue(page.contains("data-woge-action"))
        }
}
