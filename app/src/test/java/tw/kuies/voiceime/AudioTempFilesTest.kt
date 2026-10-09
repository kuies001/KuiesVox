package tw.kuies.voiceime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTempFilesTest {
    /** 唯一目錄名：debug 與 release 兩個 variant 會平行跑，不能共用同一個路徑。 */
    private fun uniqueDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "$name-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

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
        val dir = uniqueDir("kuiesvox-audio-sweep-test")
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
            if (dir.isDirectory) dir.deleteRecursively()
        }
    }

    @Test
    fun directoriesNamedLikeRecordingsAreNeverSelected() {
        val dir = uniqueDir("kuiesvox-audio-dir-test")
        val nested = File(dir, "voice-dir.wav").apply { mkdirs() }
        try {
            nested.setLastModified(1_000L)

            assertTrue(AudioTempFiles.expiredFiles(listOf(nested), 9_000L, 4_000L).isEmpty())
        } finally {
            if (dir.isDirectory) dir.deleteRecursively()
        }
    }

    @Test
    fun aRecordingStillInUseIsNeverSwept() {
        val dir = uniqueDir("kuiesvox-audio-inuse-test")
        try {
            val inUse = File(dir, "voice-active.wav").apply {
                writeText("audio")
                setLastModified(1_000L)
            }
            val stale = File(dir, "voice-stale.wav").apply {
                writeText("audio")
                setLastModified(1_000L)
            }

            val expired = AudioTempFiles.expiredFiles(
                files = listOf(inUse, stale),
                nowMillis = 9_000L,
                maxAgeMillis = 4_000L,
                inUseNames = setOf(inUse.name)
            )

            assertEquals(listOf(stale), expired)
        } finally {
            if (dir.isDirectory) dir.deleteRecursively()
        }
    }

    @Test
    fun abnormalTimestampsAreNotTreatedAsStale() {
        val dir = uniqueDir("kuiesvox-audio-clock-test")
        try {
            val unknown = File(dir, "voice-unknown.wav").apply {
                writeText("audio")
                setLastModified(0L)
            }
            val future = File(dir, "voice-future.wav").apply {
                writeText("audio")
                setLastModified(20_000L)
            }

            val expired = AudioTempFiles.expiredFiles(
                files = listOf(unknown, future),
                nowMillis = 9_000L,
                maxAgeMillis = 4_000L
            )

            assertTrue("unknown or future timestamps must be left alone", expired.isEmpty())
        } finally {
            if (dir.isDirectory) dir.deleteRecursively()
        }
    }

    @Test
    fun aFileThatDisappearedBeforeTheSweepIsNeitherSelectedNorFatal() {
        val missing = File(System.getProperty("java.io.tmpdir"), "voice-missing-${System.nanoTime()}.wav")

        assertTrue(AudioTempFiles.expiredFiles(listOf(missing), 9_000L, 4_000L).isEmpty())
        assertFalse(missing.delete())
    }
}
