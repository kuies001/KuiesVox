package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次選取工具列的「全選」控制：文字與可用狀態依搜尋框內容決定。
 * 空白與只有空格的查詢都視為沒有搜尋條件。
 */
class PersonalizationSelectAllPolicyTest {

    @Test
    fun anEmptySearchUsesThePlainSelectAllLabel() {
        assertEquals("全選", PersonalizationSelectAllPolicy.labelFor(""))
        assertFalse(PersonalizationSelectAllPolicy.hasSearchQuery(""))
    }

    @Test
    fun aWhitespaceOnlySearchIsTreatedAsNoSearchQuery() {
        assertEquals("全選", PersonalizationSelectAllPolicy.labelFor("   "))
        assertEquals("全選", PersonalizationSelectAllPolicy.labelFor("\t \n"))
        assertFalse(PersonalizationSelectAllPolicy.hasSearchQuery("   "))
    }

    @Test
    fun aSearchWithTextUsesTheSearchScopedLabel() {
        assertEquals("全選目前搜尋結果", PersonalizationSelectAllPolicy.labelFor("甲"))
        assertEquals("全選目前搜尋結果", PersonalizationSelectAllPolicy.labelFor(" 甲 "))
        assertTrue(PersonalizationSelectAllPolicy.hasSearchQuery("甲"))
    }

    @Test
    fun selectAllIsDisabledOnlyWhenTheScopeHasNothingToSelect() {
        assertTrue(PersonalizationSelectAllPolicy.isEnabled(1))
        assertTrue(PersonalizationSelectAllPolicy.isEnabled(100))
        // 搜尋 0 筆（或分頁完全沒有資料）時不能按，避免誤以為已全選。
        assertFalse(PersonalizationSelectAllPolicy.isEnabled(0))
    }
}
