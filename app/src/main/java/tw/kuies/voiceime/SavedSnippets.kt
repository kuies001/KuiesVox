package tw.kuies.voiceime

import android.content.Context
import java.util.Locale
import java.util.UUID

internal data class SavedSnippet(
    val id: String,
    val title: String,
    val content: String,
    val categoryId: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long?
)

internal data class SavedSnippetCategory(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

internal data class SavedSnippetLibrary(
    val categories: List<SavedSnippetCategory>,
    val snippets: List<SavedSnippet>
)

internal object SavedSnippetCategoryDefaults {
    const val GENERAL_ID = "general"

    val entries = listOf(
        SavedSnippetCategory(GENERAL_ID, "一般", true, 0L, 0L),
        SavedSnippetCategory("work", "工作", true, 0L, 0L),
        SavedSnippetCategory("technical", "技術", true, 0L, 0L),
        SavedSnippetCategory("custom", "自訂", true, 0L, 0L)
    )
}

internal enum class SavedSnippetSort {
    ALL,
    RECENT,
}

internal enum class SavedSnippetManagerAction {
    NEW,
    EDIT
}

internal data class SavedSnippetManagerLaunch(
    val requestId: Long,
    val action: SavedSnippetManagerAction,
    val snippetId: String? = null
)

internal object SavedSnippetOrdering {
    fun filterAndSort(
        snippets: List<SavedSnippet>,
        search: String,
        sort: SavedSnippetSort
    ): List<SavedSnippet> {
        val query = search.trim()
        val filtered = snippets.filter { snippet ->
            query.isEmpty() || snippet.title.contains(query, ignoreCase = true) ||
                snippet.content.contains(query, ignoreCase = true)
        }
        val comparator = when (sort) {
            SavedSnippetSort.ALL -> compareByDescending<SavedSnippet> { it.pinned }
                .thenBy { it.title.lowercase(Locale.ROOT) }
                .thenByDescending { it.updatedAt }
            SavedSnippetSort.RECENT -> compareByDescending<SavedSnippet> { it.lastUsedAt ?: Long.MIN_VALUE }
                .thenByDescending { it.pinned }
                .thenByDescending { it.updatedAt }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        }
        return filtered.sortedWith(comparator)
    }
}

internal enum class SavedSnippetSelectionState {
    NONE,
    PARTIAL,
    ALL
}

internal class SavedSnippetSelection {
    private val selected = linkedSetOf<String>()

    var isActive: Boolean = false
        private set

    fun enter() {
        selected.clear()
        isActive = true
    }

    fun cancel() {
        selected.clear()
        isActive = false
    }

    fun setSelected(id: String, isSelected: Boolean, visibleIds: Collection<String>) {
        if (!isActive || id !in visibleIds) return
        if (isSelected) selected.add(id) else selected.remove(id)
    }

    fun setAllSelected(isSelected: Boolean, visibleIds: Collection<String>) {
        if (!isActive) return
        if (isSelected) selected.addAll(visibleIds) else selected.removeAll(visibleIds.toSet())
    }

    fun toggleAll(visibleIds: Collection<String>) {
        if (!isActive || visibleIds.isEmpty()) return
        val allSelected = visibleIds.all(selected::contains)
        setAllSelected(!allSelected, visibleIds)
    }

    fun reconcile(visibleIds: Collection<String>) {
        selected.retainAll(visibleIds.toSet())
    }

    fun selectedIds(visibleIds: Collection<String>): Set<String> =
        selected.filterTo(linkedSetOf()) { it in visibleIds }

    fun stateFor(visibleIds: Collection<String>): SavedSnippetSelectionState {
        if (visibleIds.isEmpty()) return SavedSnippetSelectionState.NONE
        val selectedCount = visibleIds.count(selected::contains)
        return when {
            selectedCount == 0 -> SavedSnippetSelectionState.NONE
            selectedCount == visibleIds.size -> SavedSnippetSelectionState.ALL
            else -> SavedSnippetSelectionState.PARTIAL
        }
    }
}

internal data class SavedSnippetDeleteRequest(
    val ids: Set<String>,
    val batchDelete: Boolean,
    val message: String
)

internal class SavedSnippetDeleteConfirmationState {
    private var request: SavedSnippetDeleteRequest? = null

    val isVisible: Boolean
        get() = request != null

    fun show(ids: Collection<String>, batchDelete: Boolean, message: String): Boolean {
        if (request != null || ids.isEmpty()) return false
        request = SavedSnippetDeleteRequest(ids.toSet(), batchDelete, message)
        return true
    }

    fun dismiss() {
        request = null
    }

    fun invalidateMissing(existingIds: Collection<String>): Boolean {
        val pending = request ?: return false
        val existing = existingIds.toSet()
        if (pending.ids.all { it in existing }) return false
        request = null
        return true
    }

    fun consume(existingIds: Collection<String>): SavedSnippetDeleteRequest? {
        val pending = request ?: return null
        request = null
        val validIds = pending.ids.intersect(existingIds.toSet())
        return pending.copy(ids = validIds).takeIf { validIds.isNotEmpty() }
    }
}

internal fun interface SnippetCommitTarget {
    fun commitText(text: CharSequence, newCursorPosition: Int): Boolean
}

internal object SavedSnippetInsertion {
    fun insert(target: SnippetCommitTarget?, content: String): Boolean {
        if (target == null || content.isEmpty()) return false
        return runCatching { target.commitText(content, 1) }.getOrDefault(false)
    }

    fun insert(target: SnippetCommitTarget?, content: String, onSuccessfulInsert: () -> Unit): Boolean {
        if (!insert(target, content)) return false
        onSuccessfulInsert()
        return true
    }
}

internal class SavedSnippetManager(
    private val dao: SavedSnippetDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val runInTransaction: ((() -> Unit) -> Unit) = { it() }
) {
    fun load(): SavedSnippetLibrary = SavedSnippetLibrary(
        categories = dao.getCategories().map(SnippetCategoryRow::toEntity),
        snippets = dao.getSnippets().map(SavedSnippetRow::toEntity)
    )

    fun save(id: String?, title: String, content: String, categoryId: String): Boolean {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty() || content.isBlank()) return false
        val validCategoryId = dao.getCategory(categoryId)?.id ?: SavedSnippetCategoryDefaults.GENERAL_ID
        val timestamp = now()
        if (id == null) {
            dao.insertSnippet(
                SavedSnippetRow(
                    id = newId(),
                    title = normalizedTitle,
                    content = content,
                    categoryId = validCategoryId,
                    pinned = false,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    lastUsedAt = null
                )
            )
            return true
        }
        val existing = dao.getSnippet(id) ?: return false
        dao.updateSnippet(
            existing.copy(
                title = normalizedTitle,
                content = content,
                categoryId = validCategoryId,
                updatedAt = timestamp
            )
        )
        return true
    }

    fun setPinned(id: String, pinned: Boolean) {
        dao.setPinned(id, pinned, now())
    }

    fun markUsed(id: String) {
        dao.setLastUsedAt(id, now())
    }

    fun delete(id: String) = delete(setOf(id))

    fun delete(ids: Set<String>) {
        val validIds = ids.filter(String::isNotBlank).toSet()
        if (validIds.isEmpty()) return
        runInTransaction {
            validIds.forEach(dao::deleteSnippet)
        }
    }

    fun clearAll() = dao.deleteAllSnippets()

    fun createCategory(name: String): Boolean {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty() || duplicateCategory(normalizedName)) return false
        val timestamp = now()
        dao.insertCategory(
            SnippetCategoryRow(
                id = newId(),
                name = normalizedName,
                isBuiltIn = false,
                createdAt = timestamp,
                updatedAt = timestamp
            )
        )
        return true
    }

    fun renameCategory(id: String, name: String): Boolean {
        val normalizedName = name.trim()
        val category = dao.getCategory(id) ?: return false
        if (normalizedName.isEmpty() || duplicateCategory(normalizedName, excludingId = id)) return false
        dao.updateCategory(category.copy(name = normalizedName, updatedAt = now()))
        return true
    }

    fun deleteCategory(id: String): Boolean {
        if (id == SavedSnippetCategoryDefaults.GENERAL_ID) return false
        if (dao.getCategory(id) == null) return false
        runInTransaction {
            dao.moveSnippets(id, SavedSnippetCategoryDefaults.GENERAL_ID)
            dao.deleteCategory(id)
        }
        return true
    }

    private fun duplicateCategory(name: String, excludingId: String? = null): Boolean =
        dao.getCategories().any { it.id != excludingId && it.name.equals(name, ignoreCase = true) }
}

internal object SavedSnippetRepository {
    fun loadAsync(context: Context, callback: (Result<SavedSnippetLibrary>) -> Unit) {
        submit(context, callback) { it.load() }
    }

    fun saveAsync(
        context: Context,
        id: String?,
        title: String,
        content: String,
        categoryId: String,
        callback: (Result<Boolean>) -> Unit
    ) = submit(context, callback) { it.save(id, title, content, categoryId) }

    fun setPinnedAsync(
        context: Context,
        id: String,
        pinned: Boolean,
        callback: (Result<Unit>) -> Unit = {}
    ) = submit(context, callback) { it.setPinned(id, pinned) }

    fun markUsedAsync(context: Context, id: String) {
        submit(context, {}) { it.markUsed(id) }
    }

    fun deleteAsync(context: Context, id: String, callback: (Result<Unit>) -> Unit = {}) =
        deleteManyAsync(context, setOf(id), callback)

    fun deleteManyAsync(
        context: Context,
        ids: Set<String>,
        callback: (Result<Unit>) -> Unit = {}
    ) = submit(context, callback) { it.delete(ids) }

    fun clearAllAsync(context: Context, callback: (Result<Unit>) -> Unit = {}) =
        submit(context, callback) { it.clearAll() }

    fun createCategoryAsync(
        context: Context,
        name: String,
        callback: (Result<Boolean>) -> Unit
    ) = submit(context, callback) { it.createCategory(name) }

    fun renameCategoryAsync(
        context: Context,
        id: String,
        name: String,
        callback: (Result<Boolean>) -> Unit
    ) = submit(context, callback) { it.renameCategory(id, name) }

    fun deleteCategoryAsync(context: Context, id: String, callback: (Result<Boolean>) -> Unit) =
        submit(context, callback) { it.deleteCategory(id) }

    private fun <T> submit(
        context: Context,
        callback: (Result<T>) -> Unit,
        operation: (SavedSnippetManager) -> T
    ) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({
            val database = UserHistoryDatabase.get(appContext)
            operation(
                SavedSnippetManager(
                    dao = database.savedSnippetDao(),
                    runInTransaction = { block -> database.runInTransaction { block() } }
                )
            )
        }, callback)
    }
}
