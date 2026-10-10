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

    @Test
    fun legacyEntriesLoadWithoutContextAndNewContextPersists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("personal_glossary", Context.MODE_PRIVATE)
        assertTrue(preferences.edit()
            .putString("entries_v1", "[{\"id\":\"legacy\",\"term\":\"住手\",\"enabled\":true}]")
            .remove("default_data_version")
            .commit())

        try {
            val legacy = awaitResult { callback -> PersonalGlossaryRepository.load(context, callback) }
            assertEquals("住手", legacy.single().term)
            assertTrue(legacy.single().commonPhrases.isEmpty())

            val updated = awaitResult { callback ->
                PersonalGlossaryRepository.setCommonPhrases(
                    context,
                    legacy.single().id,
                    listOf("你給我住手", "快住手"),
                    callback
                )
            }
            assertEquals(listOf("你給我住手", "快住手"), updated.single().commonPhrases)

            val reloaded = awaitResult { callback -> PersonalGlossaryRepository.load(context, callback) }
            assertEquals(listOf("你給我住手", "快住手"), reloaded.single().commonPhrases)
        } finally {
            assertTrue(preferences.edit().remove("entries_v1").remove("default_data_version").commit())
        }
    }

    @Test
    fun batchDeleteRemovesOnlyTheSelectedEntriesAndIsIdempotent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("personal_glossary", Context.MODE_PRIVATE)
        assertTrue(preferences.edit().remove("entries_v1").commit())

        try {
            awaitResult { callback ->
                PersonalGlossaryRepository.add(context, listOf("甲", "乙", "丙"), callback)
            }
            val loaded = awaitResult { callback -> PersonalGlossaryRepository.load(context, callback) }
            val targets = loaded.filter { it.term == "甲" || it.term == "丙" }.map { it.id }.toSet()

            val remaining = awaitResult { callback ->
                PersonalGlossaryRepository.deleteSelected(context, targets, callback)
            }
            assertEquals(listOf("乙"), remaining.map { it.term })

            // 重複送出同一批 ID（例如連點）不會再刪掉任何東西。
            val repeated = awaitResult { callback ->
                PersonalGlossaryRepository.deleteSelected(context, targets, callback)
            }
            assertEquals(listOf("乙"), repeated.map { it.term })

            val reloaded = awaitResult { callback -> PersonalGlossaryRepository.load(context, callback) }
            assertEquals(listOf("乙"), reloaded.map { it.term })
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
