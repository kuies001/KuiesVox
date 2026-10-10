package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「個人化」批次選取的共用規則：常用詞與修正规則套用同一套選取與刪除判斷。
 * 選取一律以穩定 ID 為依據，全選只作用在目前的作用範圍（無搜尋條件時是整個分頁，
 * 有搜尋條件時是搜尋結果）。
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
    fun withoutASearchQuerySelectAllCoversEveryEntryInTheTab() {
        val selection = PersonalizationSelection().selectScope(allIds)

        assertEquals(allIds.size, selection.count)
        assertTrue(allIds.all { selection.isSelected(it) })
        assertTrue(selection.isAllSelectedIn(allIds))
    }

    @Test
    fun selectAllOnlySelectsTheCurrentSearchResults() {
        // 全部 5 筆、搜尋只命中 2 筆時，全選只會選到那 2 筆，其他 3 筆不受影響。
        val scope = listOf("b", "d")

        val selection = PersonalizationSelection().selectScope(scope)

        assertEquals(2, selection.count)
        assertEquals(setOf("b", "d"), selection.selectedIds)
        assertFalse(selection.isSelected("a"))
        assertFalse(selection.isSelected("e"))
    }

    @Test
    fun deselectAllOnlyRemovesTheScopeAndKeepsSelectionsOutsideIt() {
        val selection = PersonalizationSelection(selectedIds = setOf("a", "b", "d"))

        val afterDeselect = selection.deselectScope(listOf("b", "d"))

        assertEquals(setOf("a"), afterDeselect.selectedIds)
    }

    @Test
    fun theSelectAllCheckboxReflectsOffPartialAndFullStatesOfTheScopeOnly() {
        val scope = listOf("a", "b", "c")
        val none = PersonalizationSelection()
        val partial = none.toggle("b")
        val full = partial.toggle("a").toggle("c")

        assertFalse(none.isAllSelectedIn(scope))
        assertFalse(none.hasPartialSelectionIn(scope))

        assertFalse(partial.isAllSelectedIn(scope))
        assertTrue(partial.hasPartialSelectionIn(scope))

        assertTrue(full.isAllSelectedIn(scope))
        assertFalse(full.hasPartialSelectionIn(scope))
    }

    @Test
    fun aSelectionOutsideTheScopeDoesNotMakeTheScopeLookFullySelected() {
        // 搜尋 B 的結果只有 b、d，雖然 a 也被選取，但 B 的範圍仍未全選。
        val selection = PersonalizationSelection(selectedIds = setOf("a", "b"))

        assertFalse(selection.isAllSelectedIn(listOf("b", "d")))
        assertTrue(selection.hasPartialSelectionIn(listOf("b", "d")))
        assertTrue(selection.hasSelectionOutside(listOf("b", "d")))
    }

    @Test
    fun anEmptyScopeIsNeverReportedAsFullySelected() {
        val selection = PersonalizationSelection(selectedIds = setOf("a"))

        assertFalse(selection.isAllSelectedIn(emptyList()))
        assertFalse(selection.hasPartialSelectionIn(emptyList()))
    }

    @Test
    fun selectionsAccumulateAcrossDifferentSearches() {
        // 搜尋 A 選 3 筆，改成搜尋 B 再選 2 筆，總數應為 5 筆。
        val searchA = listOf("a1", "a2", "a3")
        val searchB = listOf("b1", "b2")

        val afterA = PersonalizationSelection().selectScope(searchA)
        val afterB = afterA.selectScope(searchB)

        assertEquals(5, afterB.count)
        assertEquals((searchA + searchB).toSet(), afterB.selectedIds)
        assertTrue(afterB.hasSelectionOutside(searchB))
    }

    @Test
    fun changingTheSearchNeitherClearsNorExtendsTheSelection() {
        val selectedInA = PersonalizationSelection().selectScope(listOf("a1", "a2", "a3"))
        val storedIds = listOf("a1", "a2", "a3", "b1", "b2")

        // 只是換搜尋條件：選取內容不變，也不會自動選取新搜尋結果。
        val afterSearchChange = selectedInA.retainExisting(storedIds)

        assertEquals(setOf("a1", "a2", "a3"), afterSearchChange.selectedIds)
    }

    @Test
    fun overlappingSearchResultsAreDeduplicatedByStableId() {
        val afterA = PersonalizationSelection().selectScope(listOf("a1", "a2"))
        val afterB = afterA.selectScope(listOf("a2", "a3"))

        assertEquals(3, afterB.count)
        assertEquals(setOf("a1", "a2", "a3"), afterB.selectedIds)
    }

    @Test
    fun deselectingOneSearchKeepsSelectionsMadeUnderAnotherSearch() {
        val afterA = PersonalizationSelection().selectScope(listOf("a1", "a2", "a3"))
        val afterB = afterA.selectScope(listOf("b1", "b2"))

        val afterDeselectingB = afterB.deselectScope(listOf("b1", "b2"))

        assertEquals(setOf("a1", "a2", "a3"), afterDeselectingB.selectedIds)
        assertEquals(3, afterDeselectingB.count)
    }

    @Test
    fun theDeletionSetCoversEverySearchAndOnlyExistingIds() {
        val afterA = PersonalizationSelection().selectScope(listOf("a1", "a2", "a3"))
        val afterB = afterA.selectScope(listOf("b1", "b2"))
        val storedIds = listOf("a1", "a2", "a3", "b1", "b2", "b3")

        // 刪除前再次核對：跨搜尋累積的 5 筆都在，另外多一個已不存在的 ID 會被排除。
        val withStale = afterB.toggle("gone")

        assertEquals(setOf("a1", "a2", "a3", "b1", "b2"), withStale.existingSelection(storedIds))
        assertEquals(setOf("a1", "b1"), withStale.existingSelection(listOf("a1", "b1")))
    }

    @Test
    fun selectionsSurviveSearchChangesAndArePrunedWhenEntriesDisappear() {
        val selection = PersonalizationSelection(selectedIds = setOf("a", "c"))

        // 搜尋條件改變（作用範圍不同）不會動到已選取的 ID。
        assertEquals(setOf("a", "c"), selection.selectScope(listOf("c")).selectedIds)

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
    fun cancellingOrBackingOutOfTheDialogKeepsTheSelectionAndTheData() {
        // 取消確認對話框只是不呼叫刪除；選取本身不會被任何純函式改動。
        val selection = PersonalizationSelection().toggle("a").toggle("b")

        assertEquals(setOf("a", "b"), selection.selectedIds)
        assertEquals(setOf("a", "b"), selection.toggle("c").toggle("c").selectedIds)
        assertEquals(allIds, allIds)
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
