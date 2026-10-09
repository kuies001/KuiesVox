package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserHistoryManagerTest {
    @Test
    fun clipboardAddsNewItem() {
        val manager = ClipboardHistoryManager(MemoryClipboardDao(), now = { 10L }, newId = { "one" })

        assertTrue(manager.record("hello", isSensitiveEditor = false))
        assertEquals(listOf(ClipboardHistoryEntity("one", "hello", 10L, false)), manager.list())
    }

    @Test
    fun clipboardDuplicateMovesToTopAndKeepsPinState() {
        val dao = MemoryClipboardDao()
        var timestamp = 1L
        var nextId = 0
        val manager = ClipboardHistoryManager(dao, now = { timestamp++ }, newId = { "id-${nextId++}" })
        manager.record("same", false)
        manager.record("other", false)
        manager.setPinned("id-0", true)

        manager.record("same", false)

        assertEquals("same", manager.list().first().text)
        assertEquals(3L, manager.list().first().createdAt)
        assertTrue(manager.list().first().pinned)
        assertEquals(1, manager.list().count { it.text == "same" })
    }

    @Test
    fun systemClipboardAcceptsPlainAndHtmlTextClips() {
        assertTrue(
            SystemClipboardCapturePolicy.canCaptureClip(
                itemCount = 1,
                hasPlainText = true,
                hasHtmlText = false,
                isSensitiveClip = false,
                isSensitiveEditor = false
            )
        )
        assertTrue(
            SystemClipboardCapturePolicy.canCaptureClip(
                itemCount = 1,
                hasPlainText = false,
                hasHtmlText = true,
                isSensitiveClip = false,
                isSensitiveEditor = false
            )
        )
    }

    @Test
    fun systemClipboardRejectsSensitiveEmptyAndNonTextClips() {
        assertFalse(
            SystemClipboardCapturePolicy.canCaptureClip(
                itemCount = 1,
                hasPlainText = true,
                hasHtmlText = false,
                isSensitiveClip = true,
                isSensitiveEditor = false
            )
        )
        assertFalse(
            SystemClipboardCapturePolicy.canCaptureClip(
                itemCount = 0,
                hasPlainText = true,
                hasHtmlText = false,
                isSensitiveClip = false,
                isSensitiveEditor = false
            )
        )
        assertFalse(
            SystemClipboardCapturePolicy.canCaptureClip(
                itemCount = 1,
                hasPlainText = false,
                hasHtmlText = false,
                isSensitiveClip = false,
                isSensitiveEditor = false
            )
        )
        assertFalse(ClipboardHistoryPolicy.canStore("  ", isSensitiveEditor = false))
    }

    @Test
    fun clipboardListenerIsLimitedToActiveNonSensitiveInput() {
        assertTrue(SystemClipboardCapturePolicy.shouldRegisterListener(false, false))
        assertFalse(SystemClipboardCapturePolicy.shouldRegisterListener(false, true))
        assertFalse(SystemClipboardCapturePolicy.shouldRegisterListener(true, false))
        assertTrue(SystemClipboardCapturePolicy.shouldReadClipboard(false, true, false))
        assertFalse(SystemClipboardCapturePolicy.shouldReadClipboard(false, false, false))
        assertFalse(SystemClipboardCapturePolicy.shouldReadClipboard(false, true, true))
        assertFalse(SystemClipboardCapturePolicy.shouldReadClipboard(true, true, false))
        assertTrue(
            SystemClipboardCapturePolicy.shouldReadClipboard(
                serviceDestroyed = false,
                listenerRegistered = false,
                isSensitiveEditor = false,
                allowWithoutListener = true
            )
        )
        assertFalse(
            SystemClipboardCapturePolicy.shouldReadClipboard(
                serviceDestroyed = false,
                listenerRegistered = false,
                isSensitiveEditor = true,
                allowWithoutListener = true
            )
        )
    }

    @Test
    fun clipboardListenerRegistersOnceAndUnregistersOnLifecycleEnd() {
        var registrations = 0
        var unregistrations = 0
        val lifecycle = ClipboardListenerLifecycle(
            registerListener = { registrations++; true },
            unregisterListener = { unregistrations++ }
        )

        assertFalse(lifecycle.registerIfAllowed(allowed = false))
        assertTrue(lifecycle.registerIfAllowed(allowed = true))
        assertFalse(lifecycle.registerIfAllowed(allowed = true))
        assertTrue(lifecycle.isRegistered)
        lifecycle.unregister()
        lifecycle.unregister()

        assertEquals(1, registrations)
        assertEquals(1, unregistrations)
        assertFalse(lifecycle.isRegistered)
    }

    @Test
    fun clipboardKeepsOnlyTwentyUnpinnedItems() {
        var nextId = 0
        var timestamp = 0L
        val manager = ClipboardHistoryManager(
            MemoryClipboardDao(),
            now = { timestamp++ },
            newId = { "id-${nextId++}" }
        )

        repeat(25) { manager.record("item-$it", false) }

        assertEquals(ClipboardHistoryPolicy.MAX_UNPINNED_ITEMS, manager.list().size)
        assertEquals("item-24", manager.list().first().text)
    }

    @Test
    fun pinnedClipboardItemsSurviveUnpinnedTrimming() {
        val dao = MemoryClipboardDao()
        var nextId = 0
        var timestamp = 0L
        val manager = ClipboardHistoryManager(dao, now = { timestamp++ }, newId = { "id-${nextId++}" })
        manager.record("pinned", false)
        manager.setPinned("id-0", true)
        repeat(22) { manager.record("item-$it", false) }

        assertEquals(21, manager.list().size)
        assertTrue(manager.list().any { it.text == "pinned" && it.pinned })
        assertEquals(20, manager.list().count { !it.pinned })
    }

    @Test
    fun clipboardCanDeleteOneItem() {
        var nextId = 0
        val manager = ClipboardHistoryManager(MemoryClipboardDao(), newId = { "id-${nextId++}" })
        manager.record("one", false)
        manager.record("two", false)

        manager.delete("id-0")

        assertEquals(listOf("two"), manager.list().map { it.text })
    }

    @Test
    fun clipboardClearRemovesOnlyUnpinnedItems() {
        var nextId = 0
        val manager = ClipboardHistoryManager(MemoryClipboardDao(), newId = { "id-${nextId++}" })
        manager.record("keep", false)
        manager.setPinned("id-0", true)
        manager.record("remove", false)

        manager.clearUnpinned()

        assertEquals(listOf("keep"), manager.list().map { it.text })
    }

    @Test
    fun clipboardDoesNotStoreInPasswordOrSensitiveEditors() {
        val manager = ClipboardHistoryManager(MemoryClipboardDao())
        val passwordType = TYPE_CLASS_TEXT or TEXT_VARIATION_PASSWORD
        val sensitive = EditorPrivacyPolicy.isSensitive(passwordType, 0)

        assertTrue(sensitive)
        assertFalse(manager.record("secret", sensitive))
        assertTrue(manager.list().isEmpty())
    }

    @Test
    fun editorPrivacyPolicyCoversPasswordVariationsAndNoLearningFlag() {
        val passwordVariations = listOf(0x80, 0x90, 0xe0)
        passwordVariations.forEach { variation ->
            assertTrue(EditorPrivacyPolicy.isSensitive(TYPE_CLASS_TEXT or variation, 0))
        }
        assertTrue(EditorPrivacyPolicy.isSensitive(TYPE_CLASS_NUMBER or NUMBER_VARIATION_PASSWORD, 0))
        assertTrue(EditorPrivacyPolicy.isSensitive(TYPE_CLASS_TEXT, IME_FLAG_NO_PERSONALIZED_LEARNING))
        assertFalse(EditorPrivacyPolicy.isSensitive(TYPE_CLASS_TEXT, 0))
    }

    @Test
    fun clipboardRejectsOnlyShortNumericVerificationCodes() {
        assertFalse(ClipboardHistoryPolicy.canStore(" 123456 ", isSensitiveEditor = false))
        assertTrue(ClipboardHistoryPolicy.canStore("order 123456", isSensitiveEditor = false))
        assertTrue(ClipboardHistoryPolicy.canStore("123456789", isSensitiveEditor = false))
    }

    @Test
    fun voiceHistoryStoresRawAndFinalTextAfterSuccessfulCommit() {
        val manager = VoiceHistoryManager(MemoryVoiceDao(), now = { 123L })

        assertTrue(manager.record("你給我助手", "你給我住手。", true, false, false))
        assertEquals(VoiceHistoryEntity(1, 123L, "你給我助手", "你給我住手。"), manager.list().single())
    }

    @Test
    fun voiceHistoryIsLimitedToFiftyItems() {
        var timestamp = 0L
        val manager = VoiceHistoryManager(MemoryVoiceDao(), now = { timestamp++ })

        repeat(55) { manager.record("raw-$it", "final-$it", true, false, false) }

        assertEquals(VoiceHistoryPolicy.MAX_ITEMS, manager.list().size)
        assertEquals("final-54", manager.list().first().finalText)
    }

    @Test
    fun voiceHistoryRejectsEmptyResults() {
        val manager = VoiceHistoryManager(MemoryVoiceDao())

        assertFalse(manager.record("", "final", true, false, false))
        assertFalse(manager.record("raw", "  ", true, false, false))
        assertTrue(manager.list().isEmpty())
    }

    @Test
    fun voiceHistoryDoesNotStoreCancelledOrFailedResults() {
        val manager = VoiceHistoryManager(MemoryVoiceDao())

        assertFalse(manager.record("raw", "final", true, true, false))
        assertFalse(manager.record("raw", "final", false, false, false))
        assertTrue(manager.list().isEmpty())
    }

    @Test
    fun voiceHistoryDoesNotStoreInPasswordEditors() {
        val manager = VoiceHistoryManager(MemoryVoiceDao())
        val sensitive = EditorPrivacyPolicy.isSensitive(TYPE_CLASS_NUMBER or NUMBER_VARIATION_PASSWORD, 0)

        assertTrue(sensitive)
        assertFalse(manager.record("raw", "final", true, false, sensitive))
        assertTrue(manager.list().isEmpty())
    }

    @Test
    fun voiceHistoryCanDeleteOneItem() {
        val manager = VoiceHistoryManager(MemoryVoiceDao())
        manager.record("raw 1", "final 1", true, false, false)
        manager.record("raw 2", "final 2", true, false, false)

        manager.delete(1)

        assertEquals(listOf("final 2"), manager.list().map { it.finalText })
    }

    @Test
    fun voiceHistoryCanClearAllItems() {
        val manager = VoiceHistoryManager(MemoryVoiceDao())
        manager.record("raw 1", "final 1", true, false, false)
        manager.record("raw 2", "final 2", true, false, false)

        manager.clearAll()

        assertTrue(manager.list().isEmpty())
    }

    private class MemoryClipboardDao : ClipboardHistoryDao {
        private val rows = linkedMapOf<String, ClipboardHistoryRow>()

        override fun getNewestFirst() = rows.values.sortedWith(
            compareByDescending<ClipboardHistoryRow> { it.createdAt }.thenByDescending { it.id }
        )

        override fun findByText(text: String) = rows.values
            .filter { it.text == text }
            .maxByOrNull { it.createdAt }

        override fun insertOrReplace(row: ClipboardHistoryRow) {
            rows[row.id] = row
        }

        override fun update(row: ClipboardHistoryRow) {
            rows[row.id] = row
        }

        override fun setPinned(id: String, pinned: Boolean) {
            rows[id]?.let { rows[id] = it.copy(pinned = pinned) }
        }

        override fun deleteById(id: String) {
            rows.remove(id)
        }

        override fun clearUnpinned() {
            rows.entries.removeIf { !it.value.pinned }
        }

        override fun trimUnpinned(maxItems: Int) {
            val keep = rows.values.filterNot { it.pinned }
                .sortedWith(compareByDescending<ClipboardHistoryRow> { it.createdAt }.thenByDescending { it.id })
                .take(maxItems)
                .mapTo(mutableSetOf()) { it.id }
            rows.entries.removeIf { !it.value.pinned && it.key !in keep }
        }
    }

    private class MemoryVoiceDao : VoiceHistoryDao {
        private val rows = linkedMapOf<Long, VoiceHistoryRow>()
        private var nextId = 1L

        override fun getNewestFirst() = rows.values.sortedWith(
            compareByDescending<VoiceHistoryRow> { it.createdAt }.thenByDescending { it.id }
        )

        override fun insert(row: VoiceHistoryRow): Long {
            val id = nextId++
            rows[id] = row.copy(id = id)
            return id
        }

        override fun deleteById(id: Long) {
            rows.remove(id)
        }

        override fun clearAll() {
            rows.clear()
        }

        override fun trim(maxItems: Int) {
            val keep = rows.values.sortedWith(
                compareByDescending<VoiceHistoryRow> { it.createdAt }.thenByDescending { it.id }
            ).take(maxItems).mapTo(mutableSetOf()) { it.id }
            rows.keys.removeIf { it !in keep }
        }
    }

    private companion object {
        const val TYPE_CLASS_TEXT = 1
        const val TYPE_CLASS_NUMBER = 2
        const val TEXT_VARIATION_PASSWORD = 0x80
        const val NUMBER_VARIATION_PASSWORD = 0x10
        const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x01000000
    }
}
