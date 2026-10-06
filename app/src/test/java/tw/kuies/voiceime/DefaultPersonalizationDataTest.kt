package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPersonalizationDataTest {
    @Test
    fun glossaryDefaultsAreImportedOnlyForAnEmptyUninitializedStore() {
        val initial = DefaultGlossaryData.migrate(emptyList(), storedVersion = 0)

        assertEquals(DefaultGlossaryData.CURRENT_VERSION, initial.version)
        assertTrue(initial.values.isNotEmpty())
        assertEquals(
            initial.values.size,
            initial.values.map { PersonalGlossaryRules.keyFor(it.term) }.distinct().size
        )

        val existing = PersonalGlossaryTerm("user-id", "My custom term", enabled = false)
        val existingDataMigration = DefaultGlossaryData.migrate(listOf(existing), storedVersion = 0)
        assertEquals(listOf(existing), existingDataMigration.values)
        assertEquals(DefaultGlossaryData.CURRENT_VERSION, existingDataMigration.version)
    }

    @Test
    fun correctionDefaultsAreImportedOnlyForAnEmptyUninitializedStore() {
        val initial = DefaultCorrectionRulesData.migrate(emptyList(), storedVersion = 0)

        assertEquals(DefaultCorrectionRulesData.CURRENT_VERSION, initial.version)
        assertTrue(initial.values.isNotEmpty())
        assertEquals(
            initial.values.size,
            initial.values.map { TextCorrectionRules.keyFor(it.sourceText) }.distinct().size
        )

        val existing = TextCorrectionRule("user-rule", "custom source", "custom target", enabled = false)
        val existingDataMigration = DefaultCorrectionRulesData.migrate(listOf(existing), storedVersion = 0)
        assertEquals(listOf(existing), existingDataMigration.values)
        assertEquals(DefaultCorrectionRulesData.CURRENT_VERSION, existingDataMigration.version)
    }

    @Test
    fun restartingDoesNotDuplicateDefaultsOrRestoreUserDeletedTerms() {
        val initial = DefaultGlossaryData.migrate(emptyList(), storedVersion = 0)
        val removed = initial.values.first { it.term == "Gemma" }
        val afterUserDelete = PersonalGlossaryRules.delete(initial.values, removed.id)

        val restarted = DefaultGlossaryData.migrate(afterUserDelete, initial.version)

        assertEquals(afterUserDelete, restarted.values)
        assertFalse(restarted.values.any { it.term == "Gemma" })
    }

    @Test
    fun userDisabledOrDeletedDefaultRulesStayThatWayAfterRestart() {
        val initial = DefaultCorrectionRulesData.migrate(emptyList(), storedVersion = 0)
        val disabledRule = initial.values.first { it.sourceText == "萊爾福" }
        val deletedRule = initial.values.first { it.sourceText == "佳樂福" }
        val afterUserChanges = TextCorrectionRules.delete(
            TextCorrectionRules.setEnabled(initial.values, disabledRule.id, enabled = false),
            deletedRule.id
        )

        val restarted = DefaultCorrectionRulesData.migrate(afterUserChanges, initial.version)

        assertEquals(afterUserChanges, restarted.values)
        assertFalse(restarted.values.first { it.id == disabledRule.id }.enabled)
        assertFalse(restarted.values.any { it.id == deletedRule.id })
    }

    @Test
    fun userChangesAndExistingRuleValuesAreNotOverwritten() {
        val customRule = TextCorrectionRule("custom-id", "萊爾福", "my replacement", enabled = false)

        val migrated = DefaultCorrectionRulesData.migrate(listOf(customRule), storedVersion = 0)

        assertEquals(listOf(customRule), migrated.values)
    }

    @Test
    fun manualGlossaryImportAddsOnlyMissingTermsAndPreservesDisabledEntries() {
        val disabledDefault = PersonalGlossaryTerm("gemma-id", "Gemma", enabled = false)
        val customEntry = PersonalGlossaryTerm("custom-id", "Custom term", enabled = true)

        val imported = DefaultGlossaryData.importMissing(listOf(disabledDefault, customEntry))
        val repeatedImport = DefaultGlossaryData.importMissing(imported.entries)

        assertEquals(DefaultGlossaryData.terms.size - 1, imported.addedCount)
        assertEquals(disabledDefault, imported.entries.first { it.id == disabledDefault.id })
        assertEquals(customEntry, imported.entries.first { it.id == customEntry.id })
        assertEquals(0, repeatedImport.addedCount)
        assertEquals(imported.entries, repeatedImport.entries)
    }

    @Test
    fun manualCorrectionImportAddsOnlyMissingRulesAndPreservesExistingRule() {
        val customizedRule = TextCorrectionRule(
            id = "custom-rule-id",
            sourceText = "萊爾福",
            replacementText = "my replacement",
            enabled = false
        )

        val imported = DefaultCorrectionRulesData.importMissing(listOf(customizedRule))
        val repeatedImport = DefaultCorrectionRulesData.importMissing(imported.rules)

        assertEquals(DefaultCorrectionRulesData.rules.size - 1, imported.addedCount)
        assertEquals(customizedRule, imported.rules.first { it.id == customizedRule.id })
        assertEquals(0, repeatedImport.addedCount)
        assertEquals(imported.rules, repeatedImport.rules)
    }
}
