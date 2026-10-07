package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceImePreviewStateTest {
    @Test
    fun successfulCommitUpdatesPreview() {
        val preview = VoiceImePreviewState()

        preview.recordCommitResult("final text", true, VoiceImeState.SUCCESS)

        assertEquals("final text", preview.lastCommittedText)
        assertEquals("final text", preview.visibleText)
    }

    @Test
    fun cancelledResultDoesNotReplacePreview() {
        val preview = previewWith("previous result")

        preview.recordCommitResult("cancelled result", true, VoiceImeState.CANCELLED)

        assertEquals("previous result", preview.visibleText)
    }

    @Test
    fun errorResultDoesNotReplacePreview() {
        val preview = previewWith("previous result")

        preview.recordCommitResult("failed result", true, VoiceImeState.ERROR)

        assertEquals("previous result", preview.visibleText)
    }

    @Test
    fun formattingFallbackCommitUpdatesPreview() {
        val preview = previewWith("previous result")

        preview.recordCommitResult(
            "post-processed fallback",
            true,
            VoiceImeState.FORMATTING_FALLBACK
        )

        assertEquals("post-processed fallback", preview.visibleText)
    }

    @Test
    fun failedOrBlankCommitDoesNotShowOrReplacePreview() {
        val emptyPreview = VoiceImePreviewState()
        emptyPreview.recordCommitResult("", true, VoiceImeState.SUCCESS)
        assertNull(emptyPreview.visibleText)

        val preview = previewWith("previous result")
        preview.recordCommitResult("new result", false, VoiceImeState.SUCCESS)
        preview.recordCommitResult("   ", true, VoiceImeState.SUCCESS)

        assertEquals("previous result", preview.visibleText)
    }

    @Test
    fun longPreviewUsesSingleLineEndEllipsisPolicy() {
        assertEquals(1, VoiceImePreviewLayout.MAX_LINES)
        assertTrue(VoiceImePreviewLayout.ELLIPSIZE_AT_END)
    }

    @Test
    fun missingPreviewIsHiddenWithoutDivider() {
        assertFalse(VoiceImePreviewLayout.shouldShowPreview(null))
        assertFalse(VoiceImePreviewLayout.shouldShowPreview("  "))
        assertTrue(VoiceImePreviewLayout.shouldShowPreview("測試"))
    }

    private fun previewWith(text: String): VoiceImePreviewState =
        VoiceImePreviewState().apply {
            recordCommitResult(text, true, VoiceImeState.SUCCESS)
        }
}
