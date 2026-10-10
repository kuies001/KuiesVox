package tw.kuies.voiceime

/**
 * 「個人化」頁面（常用詞／修正规則）共用的批次選取狀態。
 *
 * 純資料，可在 JVM 單元測試中驗證；UI 只負責顯示與呼叫這裡的動作。
 * 選取一律以穩定 ID 為依據，因此不受列表排序、搜尋結果變動或項目被刪除影響。
 */
internal data class PersonalizationSelection(
    val selectedIds: Set<String> = emptySet()
) {
    val count: Int get() = selectedIds.size

    fun isSelected(id: String): Boolean = id in selectedIds

    fun toggle(id: String): PersonalizationSelection =
        if (id in selectedIds) {
            copy(selectedIds = selectedIds - id)
        } else {
            copy(selectedIds = selectedIds + id)
        }

    /**
     * 全選只作用在傳入的作用範圍（沒有搜尋條件時是整個分頁，有條件時是搜尋結果），
     * 不會動到範圍外的其他選取。
     */
    fun selectScope(scopeIds: Collection<String>): PersonalizationSelection =
        copy(selectedIds = selectedIds + scopeIds)

    /** 取消全選同樣只作用在作用範圍內，與 [selectScope] 對稱。 */
    fun deselectScope(scopeIds: Collection<String>): PersonalizationSelection =
        copy(selectedIds = selectedIds - scopeIds.toSet())

    fun clearAll(): PersonalizationSelection = PersonalizationSelection()

    /** 資料重新載入後丟掉已不存在的 ID，避免拿舊 ID 去刪除。 */
    fun retainExisting(existingIds: Collection<String>): PersonalizationSelection {
        val existing = existingIds.toSet()
        val kept = selectedIds intersect existing
        return if (kept.size == selectedIds.size) this else copy(selectedIds = kept)
    }

    /** 刪除前再次核對：只保留目前仍存在的選取 ID。 */
    fun existingSelection(existingIds: Collection<String>): Set<String> =
        selectedIds intersect existingIds.toSet()

    /** 作用範圍內是否全部已選取（範圍為空時不算全部選取）。 */
    fun isAllSelectedIn(scopeIds: Collection<String>): Boolean =
        scopeIds.isNotEmpty() && scopeIds.all { it in selectedIds }

    /** 作用範圍內部分選取。 */
    fun hasPartialSelectionIn(scopeIds: Collection<String>): Boolean =
        scopeIds.any { it in selectedIds } && !isAllSelectedIn(scopeIds)

    /**
     * 選取內容是否包含目前作用範圍之外的項目（例如在先前搜尋條件下選取的資料），
     * 用來提示刪除筆數可能多於畫面上看到的筆數。
     */
    fun hasSelectionOutside(scopeIds: Collection<String>): Boolean {
        if (selectedIds.isEmpty()) return false
        val scope = scopeIds.toSet()
        return selectedIds.any { it !in scope }
    }

    /**
     * 刪除成功後所有選取項目都不再存在 → 可以離開選取模式；
     * 刪除失敗或只刪掉一部分時仍維持選取，讓使用者能重試。
     */
    fun shouldLeaveSelectionMode(existingIds: Collection<String>): Boolean =
        selectedIds.isNotEmpty() && selectedIds.none { it in existingIds }

    /**
     * 資料重新載入後的選取狀態：已不存在的 ID 會被丟掉；若原本選取的項目全部消失
     * （代表批次刪除成功）則回傳 null，呼叫端應離開選取模式。刪除失敗時資料未變，
     * 因此會原樣保留選取，讓使用者能重試。
     */
    fun afterDataChange(existingIds: Collection<String>): PersonalizationSelection? =
        if (shouldLeaveSelectionMode(existingIds)) null else retainExisting(existingIds)
}

/**
 * 批次選取工具列的「全選」控制：文字與可用狀態依搜尋框內容決定。
 *
 * 空白與只有空格的查詢都視為沒有搜尋條件（判斷前一律 trim）。
 */
internal object PersonalizationSelectAllPolicy {
    const val ALL_LABEL = "全選"
    const val SEARCH_LABEL = "全選目前搜尋結果"

    fun hasSearchQuery(search: String): Boolean = search.trim().isNotEmpty()

    fun labelFor(search: String): String =
        if (hasSearchQuery(search)) SEARCH_LABEL else ALL_LABEL

    /** 作用範圍沒有項目時（例如搜尋 0 筆）不能按，避免誤以為已全選。 */
    fun isEnabled(scopeSize: Int): Boolean = scopeSize > 0
}
