package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlankLineInsertionTest {
    @Test
    fun unreadableSurroundingTextUsesTheExplicitFallback() {
        assertEquals("\n\n", BlankLineInsertion.newlinesFor(null, null, canInspect = false))
        assertEquals("\n\n", BlankLineInsertion.newlinesFor("第一段", null, canInspect = true))
        assertEquals("\n\n", BlankLineInsertion.newlinesFor(null, "第二段", canInspect = true))
    }

    @Test
    fun oneBlankLineIsCreatedAfterPlainText() {
        assertEquals("\n\n", BlankLineInsertion.newlinesFor("第一段", "", canInspect = true))
    }

    @Test
    fun existingNewlinesAreCountedToAvoidDuplicates() {
        assertEquals("\n", BlankLineInsertion.newlinesFor("第一段\n", "", canInspect = true))
        assertEquals("", BlankLineInsertion.newlinesFor("第一段\n\n", "", canInspect = true))
        assertEquals("", BlankLineInsertion.newlinesFor("第一段", "\n\n第二段", canInspect = true))
        assertEquals("\n", BlankLineInsertion.newlinesFor("第一段", "\n第二段", canInspect = true))
        assertEquals("", BlankLineInsertion.newlinesFor("第一段\n", "\n第二段", canInspect = true))
    }

    @Test
    fun extraNewlinesAreNeverAdded() {
        listOf(
            "第一段" to "",
            "第一段\n" to "",
            "第一段\n\n" to "",
            "第一段\n\n\n" to "",
            "第一段" to "\n\n\n第二段",
            "第一段\n\n" to "\n\n第二段"
        ).forEach { (before, after) ->
            val insertion = BlankLineInsertion.newlinesFor(before, after, canInspect = true)
            assertTrue("insertion '$insertion' must not add more than one blank line", insertion.length <= 2)
            val trailingNewlines = before.takeLast(2).count { it == '\n' }
            val leadingNewlines = after.take(2).count { it == '\n' }
            if (trailingNewlines + leadingNewlines >= 2) {
                assertTrue(
                    "already separated by a blank line ($trailingNewlines/$leadingNewlines)",
                    insertion.isEmpty()
                )
            }
        }
    }

    @Test
    fun fallbackIsStableAndNeverInsertsMoreThanOneBlankLine() {
        assertEquals(2, BlankLineInsertion.FALLBACK.length)
        assertEquals("\n\n", BlankLineInsertion.FALLBACK)
    }
}
