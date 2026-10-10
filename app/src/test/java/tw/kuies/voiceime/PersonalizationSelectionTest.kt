package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「個人化」批次選取的共用規則：常用詞與修正规則套用同一套選取與刪除判斷。
 * 選取一律以穩定 ID 為依據，全選只作用在目前搜尋結果。
 */
class PersonalizationSelectionTest {

    private val allIds = listOf("a", "b", "c", "d", "e")

    @Test
    fun aFreshSelectionStartsEmpty() {
        val selection = PersonalizationSelection()

        assertEquals(0, selection.count)
        assertTrue(selection.selectedIds.isEmpty())
        assertTrue(allIds.none { selection.isSelected(it) })
    }

    @Test
    fun aSingleEntryCanBeToggledOnAndOff() {
        val selected = PersonalizationSelection().toggle("b")

        assertEquals(setOf("b"), selected.selectedIds)
        assertTrue(selected.isSelected("b"))

        val cleared = selected.toggle("b")

        assertEquals(0, cleared.count)
        assertFalse(cleared.isSelected("b"))
    }

    @Test
    fun severalEntriesCanBeSelectedIndependently() {
        val selection = PersonalizationSelection().toggle("a").toggle("c").toggle("e")

        assertEquals(3, selection.count)
        assertEquals(setOf("a", "c", "e"), selection.selectedIds)
        assertFalse(selection.isSelected("b"))
    }

    @Test
    fun selectAllOnlySelectsTheVisibleSearchResults() {
        // 全部 5 筆、搜尋只命中 2 筆時，全選只會選到那 2 筆，其他 3 筆不受影響。
        val visible = listOf("b", "d")

        val selection = PersonalizationSelection().selectVisible(visible)

        assertEquals(2, selection.count)
        assertEquals(setOf("b", "d"), selection.selectedIds)
        assertFalse(selection.isSelected("a"))
        assertFalse(selection.isSelected("e"))
    }

    @Test
    fun deselectAllOnlyRemovesTheVisibleResultsAndKeepsHiddenSelections() {
        val selection = PersonalizationSelection(selectedIds = setOf("a", "b", "d"))

        val afterDeselect = selection.deselectVisible(listOf("b", "d"))

        assertEquals(setOf("a"), afterDeselect.selectedIds)
    }

    @Test
    fun theSelectAllCheckboxReflectsOffPartialAndFullStates() {
        val visible = listOf("a", "b", "c")
        val none = PersonalizationSelection()
        val partial = none.toggle("b")
        val full = partial.toggle("a").toggle("c")

        assertFalse(none.isAllVisibleSelected(visible))
        assertFalse(none.hasPartialVisibleSelection(visible))

        assertFalse(partial.isAllVisibleSelected(visible))
        assertTrue(partial.hasPartialVisibleSelection(visible))

        assertTrue(full.isAllVisibleSelected(visible))
        assertFalse(full.hasPartialVisibleSelection(visible))
    }

    @Test
    fun anEmptyVisibleListIsNeverReportedAsFullySelected() {
        val selection = PersonalizationSelection(selectedIds = setOf("a"))

        assertFalse(selection.isAllVisibleSelected(emptyList()))
        assertFalse(selection.hasPartialVisibleSelection(emptyList()))
    }

    @Test
    fun selectionsSurviveSearchChangesAndArePrunedWhenEntriesDisappear() {
        val selection = PersonalizationSelection(selectedIds = setOf("a", "c"))

        // 搜尋條件改變（可見清單不同）不會動到已選取的 ID。
        assertEquals(setOf("a", "c"), selection.selectVisible(listOf("c")).selectedIds)

        // 資料重新載入後只留下仍存在的 ID。
        assertEquals(setOf("a"), selection.afterDataChange(listOf("a", "b"))?.selectedIds)
    }

    @Test
    fun aSuccessfulDeleteLeavesSelectionModeAndAFailedOneKeepsTheSelection() {
        val selection = PersonalizationSelection(selectedIds = setOf("a", "b"))

        // 刪除成功：選取的項目全部消失 → 應該離開選取模式。
        assertNull(selection.afterDataChange(listOf("c", "d")))
        assertTrue(selection.shouldLeaveSelectionMode(listOf("c", "d")))

        // 刪除失敗：資料未變 → 選取完整保留，可以重試。
        assertEquals(setOf("a", "b"), selection.afterDataChange(allIds)?.selectedIds)
        assertFalse(selection.shouldLeaveSelectionMode(allIds))
    }

    @Test
    fun anEmptySelectionNeverLeavesSelectionModeByItself() {
        val selection = PersonalizationSelection()

        assertFalse(selection.shouldLeaveSelectionMode(emptyList()))
        assertEquals(0, selection.afterDataChange(emptyList())?.count ?: 0)
    }

    @Test
    fun cancellingOrBackingOutOfTheDialogKeepsTheSelection() {
        // 取消確認對話框只是不呼叫刪除；選取本身不會被任何純函式改動。
        val selection = PersonalizationSelection().toggle("a").toggle("b")

        assertEquals(setOf("a", "b"), selection.selectedIds)
        assertEquals(setOf("a", "b"), selection.toggle("c").toggle("c").selectedIds)
    }

    @Test
    fun glossaryAndRuleSelectionsAreIndependent() {
        // 兩個分頁各自持有自己的選取狀態，切換分頁時由 UI 清空，
        // 因此常用詞的 ID 不可能被帶到修正规則分頁使用。
        val glossary = PersonalizationSelection().toggle("glossary-1")
        val rules = PersonalizationSelection()

        assertEquals(setOf("glossary-1"), glossary.selectedIds)
        assertEquals(0, rules.count)
    }
}
