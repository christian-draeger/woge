package dev.woge.registry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NavigationOverlayFeedbackVariantsTest {
    @Test
    fun `all thirteen component patterns differ only in presentation classes`() {
        val classes = Regex(" class=\"[^\"]*\"")
        assertEquals(cssExamples().replace(classes, ""), tailwindExamples().replace(classes, ""))
    }
}
