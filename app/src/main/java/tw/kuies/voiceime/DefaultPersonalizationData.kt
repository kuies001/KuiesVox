package tw.kuies.voiceime

internal data class DefaultDataMigrationResult<T>(
    val values: List<T>,
    val version: Int
)

internal object DefaultGlossaryData {
    const val CURRENT_VERSION = 1

    val terms = listOf(
        "Gemma",
        "ollama",
        "minimax",
        "AI agent",
        "Hermes",
        "Threads",
        "ig",
        "FB",
        "Re:0",
        "五姨",
        "蘇小芬",
        "張朝智",
        "三姨",
        "廣廣",
        "謝馬修",
        "吳小心",
        "王慧燕",
        "惠雯",
        "許餿水",
        "許大樹",
        "大樹",
        "ㄖㄖ",
        "屈臣氏",
        "今彩539",
        "39樂合彩",
        "乳酪星星魚",
        "萊爾富",
        "遠遠",
        "博凱",
        "小哥",
        "鄰家",
        "巡廠教練",
        "猛健樂",
        "週纖達",
        "下夜班",
        "下大夜",
        "健工",
        "健身工廠",
        "World Gym",
        "練背",
        "練胸",
        "練腿",
        "打拳擊",
        "心願便利貼",
        "南極人",
        "饗食天堂",
        "旭集",
        "YOYO",
        "悠悠哉哉",
        "愛睏",
        "家樂福",
        "花里胡哨",
        "羽衣甘藍",
        "代糖",
        "赤藻糖醇",
        "八方雲集",
        "Bingobingo",
        "素食餐",
        "支語",
        "熊",
        "KUMA",
        "右昌游泳池",
        "揮汗有禮",
        "八八口餐廳",
        "大順店",
        "凹子底",
        "義享天地",
        "博愛健身工廠",
        "河堤步道",
        "詭秘之主",
        "No.2",
        "Netflix"
    )

    private val incrementalTermsByVersion = emptyMap<Int, List<String>>()

    fun importMissing(
        existing: List<PersonalGlossaryTerm>
    ): PersonalGlossaryAddResult = PersonalGlossaryRules.add(existing, terms)

    fun migrate(
        existing: List<PersonalGlossaryTerm>,
        storedVersion: Int
    ): DefaultDataMigrationResult<PersonalGlossaryTerm> {
        var values = existing
        var version = storedVersion.coerceAtLeast(0)

        if (version < 1) {
            if (values.isEmpty()) values = PersonalGlossaryRules.add(values, terms).entries
            version = 1
        }
        while (version < CURRENT_VERSION) {
            version += 1
            values = PersonalGlossaryRules.add(
                values,
                incrementalTermsByVersion[version].orEmpty()
            ).entries
        }

        return DefaultDataMigrationResult(values, maxOf(version, storedVersion))
    }
}

internal object DefaultCorrectionRulesData {
    const val CURRENT_VERSION = 1

    val rules = listOf(
        "八方雲集" to "八方雲集",
        "八房雲集" to "八方雲集",
        "萊爾福" to "萊爾富",
        "佳樂福" to "家樂福",
        "萵苣" to "World Gym",
        "雙麒麟" to "霜冰淇淋",
        "製族餐" to "素食餐",
        "植族餐" to "素食餐",
        "冰果冰果" to "Bingobingo",
        "賓果賓果" to "Bingobingo",
        "AI Engine" to "AI agent",
        "AI 引擎" to "AI agent",
        "阿依林" to "Re:0",
        "阿伊林" to "Re:0",
        "愛依林" to "Re:0",
        "鬼秘之主" to "詭秘之主",
        "鬼祕之主" to "詭秘之主",
        "貴秘之主" to "詭秘之主",
        "Number One" to "No.1",
        "Number Two" to "No.2",
        "Max Place" to "Netflix",
        "next phrase" to "Netflix",
        "高都盟" to "高督盟",
        "心智" to "薪資",
        "新知" to "薪資",
        "柏凱" to "博凱",
        "波卡" to "博凱",
        "圓圓" to "遠遠",
        "大叔" to "大樹",
        "許饅水" to "許餿水",
        "魚鷹日記" to "語音日記",
        "雨音日記" to "語音日記",
        "雲日記" to "語音日記",
        "日日" to "ㄖㄖ",
        "芝芝" to "ㄖㄖ",
        "尋常教練" to "巡廠教練",
        "巡常教練" to "巡廠教練",
        "猛肩樂" to "猛健樂",
        "調成夜班" to "調整夜班",
        "愛困" to "愛睏",
        "吳小新" to "吳小心",
        "頂王" to "鼎王",
        "撒椒" to "灑椒",
        "全集" to "拳擊",
        "短網子" to "短網址",
        "微整" to "回診",
        "介入" to "介接",
        "deepseq" to "DeepSeek",
        "churchapt" to "ChatGPT",
        "下半時間" to "下班時間",
        "脊柱" to "幾組",
        "合體步道" to "河堤步道",
        "運動部的灰" to "揮汗有禮",
        "悠長游泳池" to "右昌游泳池",
        "悠長泳池" to "右昌游泳池",
        "接續博士" to "接續聊天模式的對話",
        "八八口" to "八八口餐廳",
        "大樹店" to "大順店",
        "凹底" to "凹子底",
        "奧斯底" to "凹子底",
        "異想天地" to "義享天地"
    )

    private val incrementalRulesByVersion = emptyMap<Int, List<Pair<String, String>>>()

    fun importMissing(
        existing: List<TextCorrectionRule>
    ): TextCorrectionRuleAddResult = TextCorrectionRules.add(existing, rules)

    fun migrate(
        existing: List<TextCorrectionRule>,
        storedVersion: Int
    ): DefaultDataMigrationResult<TextCorrectionRule> {
        var values = existing
        var version = storedVersion.coerceAtLeast(0)

        if (version < 1) {
            if (values.isEmpty()) values = TextCorrectionRules.add(values, rules).rules
            version = 1
        }
        while (version < CURRENT_VERSION) {
            version += 1
            values = TextCorrectionRules.add(
                values,
                incrementalRulesByVersion[version].orEmpty()
            ).rules
        }

        return DefaultDataMigrationResult(values, maxOf(version, storedVersion))
    }
}

internal data class PronounPreference(
    val subject: String,
    val pronoun: String,
    val preferredPronoun: String,
    val maximumDistanceCodePoints: Int
)

internal object DefaultPronounPreferences {
    val rules = listOf(
        PronounPreference(
            subject = "王慧燕",
            pronoun = "他",
            preferredPronoun = "她",
            maximumDistanceCodePoints = 12
        )
    )
}

internal object PronounPreferenceProcessor {
    private val sentenceBoundaries = setOf('\n', '\r', '。', '！', '？', '!', '?', '.', '；', ';')

    fun apply(text: String, preferences: List<PronounPreference>): String {
        if (text.isEmpty() || preferences.isEmpty()) return text
        val replacements = mutableMapOf<Int, Pair<Int, String>>()

        preferences.forEach { preference ->
            if (preference.subject.isEmpty() || preference.pronoun.isEmpty()) return@forEach
            var searchFrom = 0
            while (searchFrom < text.length) {
                val subjectStart = text.indexOf(preference.subject, searchFrom)
                if (subjectStart < 0) break
                searchFrom = subjectStart + preference.subject.length
                val subjectEnd = subjectStart + preference.subject.length
                var clauseStart = subjectStart
                while (clauseStart > 0 && text[clauseStart - 1] !in sentenceBoundaries) clauseStart -= 1
                var clauseEnd = subjectEnd
                while (clauseEnd < text.length && text[clauseEnd] !in sentenceBoundaries) clauseEnd += 1

                var pronounStart = text.indexOf(preference.pronoun, clauseStart)
                while (pronounStart >= 0 && pronounStart + preference.pronoun.length <= clauseEnd) {
                    val pronounEnd = pronounStart + preference.pronoun.length
                    val gapStart = minOf(subjectEnd, pronounEnd)
                    val gapEnd = maxOf(subjectStart, pronounStart)
                    val distance = text.codePointCount(gapStart, gapEnd)
                    if (distance <= preference.maximumDistanceCodePoints) {
                        replacements.putIfAbsent(
                            pronounStart,
                            preference.pronoun.length to preference.preferredPronoun
                        )
                    }
                    pronounStart = text.indexOf(preference.pronoun, pronounEnd)
                }
            }
        }

        if (replacements.isEmpty()) return text
        val result = StringBuilder(text.length)
        var cursor = 0
        replacements.toSortedMap().forEach { (start, replacement) ->
            if (start < cursor) return@forEach
            result.append(text, cursor, start)
            result.append(replacement.second)
            cursor = start + replacement.first
        }
        result.append(text, cursor, text.length)
        return result.toString()
    }
}
