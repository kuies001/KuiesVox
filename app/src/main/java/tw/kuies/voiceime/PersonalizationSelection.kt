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
     * 全選只作用在傳入的可見項目（目前搜尋結果），不會動到被搜尋條件濾掉的其他選取。
     */
    fun selectVisible(visibleIds: Collection<String>): PersonalizationSelection =
        copy(selectedIds = selectedIds + visibleIds)

    /** 取消全選同樣只作用在可見項目，與 [selectVisible] 對稱。 */
    fun deselectVisible(visibleIds: Collection<String>): PersonalizationSelection =
        copy(selectedIds = selectedIds - visibleIds.toSet())

    fun clearAll(): PersonalizationSelection = PersonalizationSelection()

    /** 資料重新載入後丟掉已不存在的 ID，避免拿舊 ID 去刪除。 */
    fun retainExisting(existingIds: Collection<String>): PersonalizationSelection {
        val existing = existingIds.toSet()
        val kept = selectedIds intersect existing
        return if (kept.size == selectedIds.size) this else copy(selectedIds = kept)
    }

    fun isAllVisibleSelected(visibleIds: Collection<String>): Boolean =
        visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }

    fun hasPartialVisibleSelection(visibleIds: Collection<String>): Boolean =
        visibleIds.any { it in selectedIds } && !isAllVisibleSelected(visibleIds)

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
