package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 個人化資料的隔離契約：新安裝一律是空的、讀取不會寫入、覆蓋安裝不動既有資料、
 * 手動編輯仍正常，而且舊版用來植入開發者私人預設資料的物件已經不存在。
 */
class PersonalizationDataIsolationTest {

    @Test
    fun aFreshInstallDecodesToAnEmptyGlossary() {
        assertTrue(PersonalGlossaryCodec.decode(null).isEmpty())
        assertTrue(PersonalGlossaryCodec.decode("").isEmpty())
        assertTrue(PersonalGlossaryCodec.decode("[]").isEmpty())
    }

    @Test
    fun aFreshInstallDecodesToNoCorrectionRules() {
        assertTrue(TextCorrectionRuleCodec.decode(null).isEmpty())
        assertTrue(TextCorrectionRuleCodec.decode("").isEmpty())
        assertTrue(TextCorrectionRuleCodec.decode("[]").isEmpty())
    }

    @Test
    fun encodingAnEmptyCollectionStaysEmptyInsteadOfInjectingDefaults() {
        assertTrue(PersonalGlossaryCodec.decode(PersonalGlossaryCodec.encode(emptyList())).isEmpty())
        assertTrue(TextCorrectionRuleCodec.decode(TextCorrectionRuleCodec.encode(emptyList())).isEmpty())
    }

    @Test
    fun anExistingGlossaryStoreIsReturnedUnchangedAndNeverExtended() {
        val stored = """[{"id":"keep-1","term":"我的詞彙","enabled":false,"commonPhrases":["例句"]}]"""

        val decoded = PersonalGlossaryCodec.decode(stored)

        assertEquals(1, decoded.size)
        assertEquals("keep-1", decoded.single().id)
        assertEquals("我的詞彙", decoded.single().term)
        assertFalse(decoded.single().enabled)
        assertEquals(listOf("例句"), decoded.single().commonPhrases)
        // 再讀一次、或經過一次編碼後再讀，都不會多出任何項目。
        assertEquals(decoded, PersonalGlossaryCodec.decode(stored))
        assertEquals(decoded, PersonalGlossaryCodec.decode(PersonalGlossaryCodec.encode(decoded)))
    }

    @Test
    fun anExistingRuleStoreIsReturnedUnchangedAndNeverExtended() {
        val stored =
            """[{"id":"keep-2","sourceText":"錯誤字","replacementText":"正確字","enabled":false}]"""

        val decoded = TextCorrectionRuleCodec.decode(stored)

        assertEquals(1, decoded.size)
        assertEquals("keep-2", decoded.single().id)
        assertEquals("錯誤字", decoded.single().sourceText)
        assertEquals("正確字", decoded.single().replacementText)
        assertFalse(decoded.single().enabled)
        assertEquals(decoded, TextCorrectionRuleCodec.decode(stored))
        assertEquals(decoded, TextCorrectionRuleCodec.decode(TextCorrectionRuleCodec.encode(decoded)))
    }

    @Test
    fun manualGlossaryEditingKeepsOtherEntriesAndTheirDisabledState() {
        val existing = listOf(
            PersonalGlossaryTerm("keep", "原有詞彙", enabled = false),
            PersonalGlossaryTerm("edit", "要刪除的詞彙", enabled = true)
        )
        val store = PersonalGlossaryCodec.decode(PersonalGlossaryCodec.encode(existing))

        val added = PersonalGlossaryRules.add(store, listOf("新詞彙", "新詞彙"))
        assertEquals(1, added.addedCount)
        val deleted = PersonalGlossaryRules.delete(added.entries, "edit")
        val toggled = PersonalGlossaryRules.setEnabled(deleted, "keep", enabled = true)

        assertEquals(2, toggled.size)
        assertEquals("原有詞彙", toggled.first { it.id == "keep" }.term)
        assertTrue(toggled.first { it.id == "keep" }.enabled)
        assertTrue(toggled.any { it.term == "新詞彙" })
        assertFalse(toggled.any { it.id == "edit" })
        assertEquals(toggled, PersonalGlossaryCodec.decode(PersonalGlossaryCodec.encode(toggled)))
    }

    @Test
    fun manualCorrectionRuleEditingKeepsOtherRulesAndTheirDisabledState() {
        val existing = listOf(
            TextCorrectionRule("keep", "來源A", "替換A", enabled = false),
            TextCorrectionRule("edit", "來源B", "替換B", enabled = true)
        )
        val store = TextCorrectionRuleCodec.decode(TextCorrectionRuleCodec.encode(existing))

        val added = TextCorrectionRules.add(store, listOf("來源C" to "替換C"))
        assertEquals(1, added.addedCount)
        val deleted = TextCorrectionRules.delete(added.rules, "edit")
        val toggled = TextCorrectionRules.setEnabled(deleted, "keep", enabled = true)

        assertEquals(2, toggled.size)
        assertTrue(toggled.first { it.id == "keep" }.enabled)
        assertTrue(toggled.any { it.sourceText == "來源C" })
        assertFalse(toggled.any { it.id == "edit" })
        assertEquals(toggled, TextCorrectionRuleCodec.decode(TextCorrectionRuleCodec.encode(toggled)))
    }

    @Test
    fun theLegacyImplantObjectsAreGoneFromTheApp() {
        // 舊版會用這三個物件在讀取時植入開發者私人詞庫、修正规則與代詞偏好；
        // 它們不存在就代表沒有自動植入的路徑了。
        listOf("DefaultGlossaryData", "DefaultCorrectionRulesData", "DefaultPronounPreferences")
            .forEach { name ->
                val missing = try {
                    Class.forName("tw.kuies.voiceime.$name")
                    false
                } catch (_: ClassNotFoundException) {
                    true
                }
                assertTrue("$name must not ship in the app", missing)
            }
    }

    @Test
    fun pronounPreferencesStayInertUnlessExplicitlyPassed() {
        assertEquals(
            "小明說他今天會到。",
            TextPostProcessor.processWithPlan("小明說他今天會到。", emptyList()).text
        )
    }
}
