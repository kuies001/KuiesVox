package tw.kuies.voiceime

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedSnippetsTest {
    @Test
    fun createEditDeleteAndRejectBlankContent() {
        var time = 10L
        val dao = FakeSavedSnippetDao()
        val manager = SavedSnippetManager(
            dao = dao,
            now = { time++ },
            newId = { "snippet-${dao.snippets.size}" }
        )

        assertFalse(manager.save(null, "空白內容", " \n ", "general"))
        assertFalse(manager.save(null, "", "有內容", "general"))
        assertTrue(manager.save(null, "工作回覆", "第一行\n第二行 🙂", "work"))
        val created = manager.load().snippets.single()
        assertEquals("工作回覆", created.title)
        assertEquals("第一行\n第二行 🙂", created.content)
        assertEquals("work", created.categoryId)
        assertEquals(10L, created.createdAt)
        assertNull(created.lastUsedAt)
        manager.setPinned(created.id, true)
        manager.markUsed(created.id)
        assertTrue(manager.load().snippets.single().pinned)
        assertTrue((manager.load().snippets.single().lastUsedAt ?: 0L) > 0L)

        assertTrue(manager.save(created.id, "工作回覆（更新）", "新內容", "technical"))
        val edited = manager.load().snippets.single()
        assertEquals(created.createdAt, edited.createdAt)
        assertTrue(edited.updatedAt > created.updatedAt)
        assertEquals("technical", edited.categoryId)
        assertTrue(edited.pinned)
        manager.delete(edited.id)
        assertTrue(manager.load().snippets.isEmpty())
    }

    @Test
    fun categoryCrudKeepsSnippetsAndGeneralCategoryCannotBeDeleted() {
        val dao = FakeSavedSnippetDao()
        val manager = SavedSnippetManager(dao, now = { 100L }, newId = { "custom-category" })
        assertTrue(manager.save(null, "範例", "內容", "work"))
        assertTrue(manager.createCategory("旅遊"))
        val category = manager.load().categories.first { it.id == "custom-category" }
        assertTrue(manager.renameCategory(category.id, "出差"))
        assertEquals("出差", manager.load().categories.first { it.id == category.id }.name)
        assertFalse(manager.createCategory(" 出差 "))

        assertFalse(manager.deleteCategory(SavedSnippetCategoryDefaults.GENERAL_ID))
        assertTrue(manager.deleteCategory("work"))
        val library = manager.load()
        assertEquals("general", library.snippets.single().categoryId)
        assertFalse(library.categories.any { it.id == "work" })
    }

    @Test
    fun favoriteSearchAndRecentOrTitleOrderingAreStable() {
        val snippets = listOf(
            SavedSnippet("b", "Zeta", "搜尋文字", "work", false, 1, 1, 30),
            SavedSnippet("a", "Alpha", "內容", "general", false, 1, 1, 10),
            SavedSnippet("c", "Beta", "另一段", "work", true, 1, 1, null)
        )

        val recent = SavedSnippetOrdering.filterAndSort(
            snippets,
            search = "搜尋",
            categoryId = "work",
            sort = SavedSnippetSort.RECENT
        )
        assertEquals(listOf("b"), recent.map { it.id })

        val byTitle = SavedSnippetOrdering.filterAndSort(
            snippets,
            search = "",
            categoryId = null,
            sort = SavedSnippetSort.TITLE
        )
        assertEquals(listOf("c", "a", "b"), byTitle.map { it.id })

        val favoriteFirst = snippets.map { if (it.id == "b") it.copy(pinned = true) else it }
        assertEquals(
            listOf("b", "c", "a"),
            SavedSnippetOrdering.filterAndSort(favoriteFirst, "", null, SavedSnippetSort.RECENT).map { it.id }
        )
    }

    @Test
    fun batchSelectionTracksSingleMultipleAllAndPartialVisibleSelections() {
        val selection = SavedSnippetSelection()
        val visibleIds = listOf("work-1", "work-2", "work-3")

        selection.enter()
        assertTrue(selection.isActive)
        assertEquals(SavedSnippetSelectionState.NONE, selection.stateFor(visibleIds))
        assertTrue(selection.selectedIds(visibleIds).isEmpty())

        selection.setSelected("work-1", true, visibleIds)
        assertEquals(setOf("work-1"), selection.selectedIds(visibleIds))
        assertEquals(SavedSnippetSelectionState.PARTIAL, selection.stateFor(visibleIds))
        selection.setSelected("work-2", true, visibleIds)
        assertEquals(setOf("work-1", "work-2"), selection.selectedIds(visibleIds))

        selection.toggleAll(visibleIds)
        assertEquals(visibleIds.toSet(), selection.selectedIds(visibleIds))
        assertEquals(SavedSnippetSelectionState.ALL, selection.stateFor(visibleIds))

        selection.setSelected("work-2", false, visibleIds)
        assertEquals(SavedSnippetSelectionState.PARTIAL, selection.stateFor(visibleIds))
        assertEquals(setOf("work-1", "work-3"), selection.selectedIds(visibleIds))
        selection.toggleAll(visibleIds)
        assertEquals(visibleIds.toSet(), selection.selectedIds(visibleIds))
        selection.toggleAll(visibleIds)
        assertTrue(selection.selectedIds(visibleIds).isEmpty())
        assertEquals(SavedSnippetSelectionState.NONE, selection.stateFor(visibleIds))

        visibleIds.forEach { selection.setSelected(it, true, visibleIds) }
        assertEquals(SavedSnippetSelectionState.ALL, selection.stateFor(visibleIds))
        selection.reconcile(listOf("work-1", "work-3"))
        assertEquals(setOf("work-1", "work-3"), selection.selectedIds(visibleIds))
        assertEquals(SavedSnippetSelectionState.ALL, selection.stateFor(listOf("work-1", "work-3")))
        selection.reconcile(emptyList())
        assertTrue(selection.selectedIds(visibleIds).isEmpty())
        assertEquals(SavedSnippetSelectionState.NONE, selection.stateFor(emptyList()))
    }

    @Test
    fun selectAllIsLimitedToCurrentSearchAndCategoryResults() {
        val snippets = listOf(
            SavedSnippet("work-email", "工作 Email", "work@example.com", "work", false, 1, 1, null),
            SavedSnippet("home-email", "私人 Email", "home@example.com", "general", false, 1, 1, null),
            SavedSnippet("work-address", "公司地址", "地址內容", "work", false, 1, 1, null),
            SavedSnippet("technical", "GitHub", "git status", "technical", false, 1, 1, null)
        )
        val selection = SavedSnippetSelection().apply { enter() }

        val emailResults = SavedSnippetOrdering.filterAndSort(
            snippets,
            search = "Email",
            categoryId = null,
            sort = SavedSnippetSort.TITLE
        ).map(SavedSnippet::id)
        selection.setAllSelected(true, emailResults)
        assertEquals(setOf("work-email", "home-email"), selection.selectedIds(emailResults))
        assertEquals(2, selection.selectedIds(emailResults).size)

        val workResults = SavedSnippetOrdering.filterAndSort(
            snippets,
            search = "",
            categoryId = "work",
            sort = SavedSnippetSort.TITLE
        ).map(SavedSnippet::id)
        selection.reconcile(workResults)
        assertEquals(setOf("work-email"), selection.selectedIds(workResults))
        selection.setAllSelected(true, workResults)
        assertEquals(setOf("work-email", "work-address"), selection.selectedIds(workResults))
        assertEquals(SavedSnippetSelectionState.ALL, selection.stateFor(workResults))
    }

    @Test
    fun cancelBatchSelectionClearsSelectionAndSingleAndBatchDeletesShareManagerPath() {
        val dao = FakeSavedSnippetDao()
        val manager = SavedSnippetManager(dao, now = { 1L }, newId = { "id-${dao.snippets.size}" })
        assertTrue(manager.save(null, "one", "content one", "general"))
        assertTrue(manager.save(null, "two", "content two", "general"))
        assertTrue(manager.save(null, "three", "content three", "general"))
        val ids = manager.load().snippets.map { it.id }
        val selection = SavedSnippetSelection().apply {
            enter()
            setSelected(ids[0], true, ids)
            setSelected(ids[2], true, ids)
        }
        assertEquals(setOf(ids[0], ids[2]), selection.selectedIds(ids))
        manager.delete(selection.selectedIds(ids))
        assertEquals(listOf(ids[1]), manager.load().snippets.map { it.id })

        manager.delete(ids[1])
        assertTrue(manager.load().snippets.isEmpty())

        selection.cancel()
        assertFalse(selection.isActive)
        assertTrue(selection.selectedIds(ids).isEmpty())
    }

    @Test
    fun insertionPassesEntireMultilineTextOnceAndFailsSafelyWithoutConnection() {
        val content = "您好，已收到。\ncommit 到 GitHub ✅"
        var calls = 0
        var committed = "before selected after"
        val target = SnippetCommitTarget { text, newCursorPosition ->
            calls += 1
            assertEquals(1, newCursorPosition)
            committed = "before ${text} after"
            true
        }

        assertTrue(SavedSnippetInsertion.insert(target, content))
        assertEquals("before ${content} after", committed)
        assertEquals(1, calls)
        assertFalse(SavedSnippetInsertion.insert(null, content))
        assertFalse(SavedSnippetInsertion.insert(target, ""))
        assertFalse(
            SavedSnippetInsertion.insert(
                SnippetCommitTarget { _, _ -> throw IllegalStateException("connection ended") },
                content
            )
        )
    }

    @Test
    fun passwordAndPinFieldsAreMarkedSensitiveForSnippetVisibility() {
        assertTrue(
            EditorPrivacyPolicy.isSensitive(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                0
            )
        )
        assertTrue(
            EditorPrivacyPolicy.isSensitive(
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                0
            )
        )
        assertFalse(EditorPrivacyPolicy.isSensitive(InputType.TYPE_CLASS_TEXT, 0))
    }

    private class FakeSavedSnippetDao : SavedSnippetDao {
        val categories = linkedMapOf<String, SnippetCategoryRow>().apply {
            SavedSnippetCategoryDefaults.entries.forEach { category ->
                put(
                    category.id,
                    SnippetCategoryRow(
                        category.id,
                        category.name,
                        category.isBuiltIn,
                        category.createdAt,
                        category.updatedAt
                    )
                )
            }
        }
        val snippets = linkedMapOf<String, SavedSnippetRow>()

        override fun getCategories(): List<SnippetCategoryRow> =
            categories.values.sortedWith(compareBy<SnippetCategoryRow> { it.name })

        override fun getCategory(id: String): SnippetCategoryRow? = categories[id]

        override fun insertCategory(category: SnippetCategoryRow) {
            require(categories.values.none { it.name.equals(category.name, ignoreCase = true) })
            categories[category.id] = category
        }

        override fun updateCategory(category: SnippetCategoryRow) {
            categories[category.id] = category
        }

        override fun deleteCategory(id: String) {
            categories.remove(id)
        }

        override fun getSnippets(): List<SavedSnippetRow> = snippets.values.toList()

        override fun getSnippet(id: String): SavedSnippetRow? = snippets[id]

        override fun insertSnippet(snippet: SavedSnippetRow) {
            snippets[snippet.id] = snippet
        }

        override fun updateSnippet(snippet: SavedSnippetRow) {
            snippets[snippet.id] = snippet
        }

        override fun setPinned(id: String, pinned: Boolean, updatedAt: Long) {
            snippets[id]?.let { snippets[id] = it.copy(pinned = pinned, updatedAt = updatedAt) }
        }

        override fun setLastUsedAt(id: String, timestamp: Long) {
            snippets[id]?.let { snippets[id] = it.copy(lastUsedAt = timestamp) }
        }

        override fun deleteSnippet(id: String) {
            snippets.remove(id)
        }

        override fun deleteAllSnippets() {
            snippets.clear()
        }

        override fun moveSnippets(sourceCategoryId: String, targetCategoryId: String) {
            snippets.entries.toList().forEach { (id, snippet) ->
                if (snippet.categoryId == sourceCategoryId) {
                    snippets[id] = snippet.copy(categoryId = targetCategoryId)
                }
            }
        }
    }
}
