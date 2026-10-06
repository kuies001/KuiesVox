package tw.kuies.voiceime

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalGlossaryRepositoryTest {
    @Test
    fun entriesPersistAcrossRepositoryReloads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("personal_glossary", Context.MODE_PRIVATE)
        assertTrue(preferences.edit().remove("entries_v1").commit())

        try {
            val added = awaitResult { callback ->
                PersonalGlossaryRepository.add(
                    context,
                    listOf(" DeepSeek ", "DeepSeek", "南岡山"),
                    callback
                )
            }
            assertEquals(2, added.addedCount)

            val loaded = awaitResult { callback -> PersonalGlossaryRepository.load(context, callback) }
            assertEquals(listOf("DeepSeek", "南岡山"), loaded.map { it.term })

            val disabled = awaitResult { callback ->
                PersonalGlossaryRepository.setEnabled(context, loaded.first().id, false, callback)
            }
            assertFalse(disabled.first().enabled)

            val reloaded = awaitResult { callback -> PersonalGlossaryRepository.load(context, callback) }
            assertFalse(reloaded.first().enabled)

            val deleted = awaitResult { callback ->
                PersonalGlossaryRepository.delete(context, reloaded.first().id, callback)
            }
            assertEquals(listOf("南岡山"), deleted.map { it.term })
        } finally {
            assertTrue(preferences.edit().remove("entries_v1").commit())
        }
    }

    private fun <T> awaitResult(register: ((Result<T>) -> Unit) -> Unit): T {
        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        register {
            result = it
            latch.countDown()
        }
        assertTrue("Storage callback timed out", latch.await(10, TimeUnit.SECONDS))
        return result!!.getOrThrow()
    }
}
