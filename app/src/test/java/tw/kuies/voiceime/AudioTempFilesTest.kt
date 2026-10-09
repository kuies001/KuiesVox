package tw.kuies.voiceime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTempFilesTest {
    @Test
    fun onlyThisAppsRecordingFilesAreRecognised() {
        assertTrue(AudioTempFiles.isTempRecordingFile("voice-1234.wav"))
        assertTrue(AudioTempFiles.isTempRecordingFile("voice-abc.wav"))

        assertFalse(AudioTempFiles.isTempRecordingFile("voice-1234.wav.part"))
        assertFalse(AudioTempFiles.isTempRecordingFile("voice-1234.raw"))
        assertFalse(AudioTempFiles.isTempRecordingFile("notes.wav"))
        assertFalse(AudioTempFiles.isTempRecordingFile("KuiesVox-v0.14.0.apk"))
        assertFalse(AudioTempFiles.isTempRecordingFile("voice-"))
    }

    @Test
    fun onlyStaleRecordingsAreSelectedAndOthersSurviveTheSweep() {
        val dir = File(System.getProperty("java.io.tmpdir"), "kuiesvox-audio-sweep-test")
        dir.mkdirs()
        try {
            val stale = File(dir, "voice-stale.wav").apply {
                writeText("audio")
                setLastModified(1_000L)
            }
            val inUse = File(dir, "voice-fresh.wav").apply {
                writeText("audio")
                setLastModified(9_000L)
            }
            val foreign = File(dir, "notes.txt").apply {
                writeText("keep me")
                setLastModified(1_000L)
            }
            val nowMillis = 9_000L

            val expired = AudioTempFiles.expiredFiles(
                files = listOf(stale, inUse, foreign),
                nowMillis = nowMillis,
                maxAgeMillis = 4_000L
            )

            assertEquals(listOf(stale), expired)
            expired.forEach { assertTrue(it.delete()) }
            assertFalse(stale.exists())
            assertTrue(inUse.exists())
            assertTrue(foreign.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun directoriesNamedLikeRecordingsAreNeverSelected() {
        val dir = File(System.getProperty("java.io.tmpdir"), "kuiesvox-audio-dir-test")
        val nested = File(dir, "voice-dir.wav").apply { mkdirs() }
        try {
            nested.setLastModified(1_000L)

            assertTrue(AudioTempFiles.expiredFiles(listOf(nested), 9_000L, 4_000L).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
