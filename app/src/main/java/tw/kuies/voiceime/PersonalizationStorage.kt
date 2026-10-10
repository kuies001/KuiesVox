package tw.kuies.voiceime

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 個人詞庫與修正规則的 JSON 儲存格式（純函式，可用 JVM 單元測試驗證）。
 *
 * 契約：解碼只還原已存放的內容，**不會**加入任何內建預設；編碼只寫入呼叫端給的清單。
 * 因此新安裝的詞庫與修正规則一定是空的，覆蓋安裝也不會動到使用者既有資料。
 */
internal object PersonalGlossaryCodec {
    fun decode(storedJson: String?): List<PersonalGlossaryTerm> {
        val jsonEntries = storedJson?.takeIf(String::isNotBlank)?.let(::JSONArray) ?: JSONArray()
        val entries = mutableListOf<PersonalGlossaryTerm>()
        val knownTerms = mutableSetOf<String>()
        val knownIds = mutableSetOf<String>()

        for (index in 0 until jsonEntries.length()) {
            val jsonEntry = jsonEntries.optJSONObject(index) ?: continue
            val term = jsonEntry.optString("term", "").trim()
            if (term.isEmpty() || !knownTerms.add(PersonalGlossaryRules.keyFor(term))) continue

            val storedId = jsonEntry.optString("id", "").trim()
            val id = storedId.takeIf { it.isNotEmpty() && knownIds.add(it) }
                ?: UUID.randomUUID().toString().also { knownIds.add(it) }
            entries += PersonalGlossaryTerm(
                id = id,
                term = term,
                enabled = jsonEntry.optBoolean("enabled", true),
                commonPhrases = jsonEntry.optJSONArray("commonPhrases")?.let { phrases ->
                    buildList {
                        for (phraseIndex in 0 until phrases.length()) {
                            phrases.optString(phraseIndex).trim()
                                .takeIf(String::isNotEmpty)?.let(::add)
                        }
                    }.distinctBy(PersonalGlossaryRules::keyFor)
                        .take(PersonalGlossaryRules.MAX_COMMON_PHRASES)
                        .map { phrase ->
                            val end = phrase.offsetByCodePoints(
                                0,
                                minOf(
                                    phrase.codePointCount(0, phrase.length),
                                    PersonalGlossaryRules.MAX_PHRASE_CODE_POINTS
                                )
                            )
                            phrase.substring(0, end)
                        }
                }.orEmpty()
            )
        }

        return entries
    }

    fun encode(entries: List<PersonalGlossaryTerm>): String {
        val jsonEntries = JSONArray()
        entries.forEach { entry ->
            jsonEntries.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("term", entry.term)
                    .put("enabled", entry.enabled)
                    .put("commonPhrases", JSONArray(entry.commonPhrases))
            )
        }
        return jsonEntries.toString()
    }
}

/** 修正规則的 JSON 儲存格式；契約與 [PersonalGlossaryCodec] 相同。 */
internal object TextCorrectionRuleCodec {
    fun decode(storedJson: String?): List<TextCorrectionRule> {
        val jsonRules = storedJson?.takeIf(String::isNotBlank)?.let(::JSONArray) ?: JSONArray()
        val rules = mutableListOf<TextCorrectionRule>()
        val knownSources = mutableSetOf<String>()
        val knownIds = mutableSetOf<String>()

        for (index in 0 until jsonRules.length()) {
            val jsonRule = jsonRules.optJSONObject(index) ?: continue
            val source = jsonRule.optString("sourceText", "").trim()
            if (source.isEmpty() || !knownSources.add(TextCorrectionRules.keyFor(source))) continue

            val storedId = jsonRule.optString("id", "").trim()
            val id = storedId.takeIf { it.isNotEmpty() && knownIds.add(it) }
                ?: UUID.randomUUID().toString().also { knownIds.add(it) }
            rules += TextCorrectionRule(
                id = id,
                sourceText = source,
                replacementText = jsonRule.optString("replacementText", "").trim(),
                enabled = jsonRule.optBoolean("enabled", true)
            )
        }

        return rules
    }

    fun encode(rules: List<TextCorrectionRule>): String {
        val jsonRules = JSONArray()
        rules.forEach { rule ->
            jsonRules.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("sourceText", rule.sourceText)
                    .put("replacementText", rule.replacementText)
                    .put("enabled", rule.enabled)
            )
        }
        return jsonRules.toString()
    }
}
