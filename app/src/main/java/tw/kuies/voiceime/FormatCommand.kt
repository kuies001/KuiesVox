package tw.kuies.voiceime

/** 一般語音模式與手動啟動的格式指令模式。 */
internal enum class VoiceInputMode {
    NORMAL,
    FORMAT_COMMAND
}

internal sealed interface FormatCommandInterpretation {
    /** 單純換行，可在本機完成。 */
    data object Newline : FormatCommandInterpretation

    /** 空一行，可在本機完成，但需要游標前後文字才能避免重複空行。 */
    data object BlankLine : FormatCommandInterpretation

    /** 明確不支援（例如破壞性指令），不得插入任何文字。 */
    data object Unsupported : FormatCommandInterpretation

    /** 交給 AI 格式整理。 */
    data object UseAi : FormatCommandInterpretation
}

/**
 * 第一層：本機確定性指令。
 * 只比對整句正規化後的完整字串，不做任意子字串比對，
 * 因此「我覺得換行很好用」不會被當成單純的換行命令。
 */
internal object FormatCommandInterpreter {
    private const val NEWLINE = "換行"
    private const val BLANK_LINE = "空一行"

    private val blockedCommands = setOf(
        "刪除全部文字",
        "刪除全部內容",
        "刪除所有文字",
        "刪除所有內容",
        "刪除全部",
        "清空全部文字",
        "清空全部內容",
        "清空所有文字",
        "清空全部",
        "清除全部文字",
        "清除所有文字",
        "清除全部"
    )

    private const val TRIMMABLE = " \t\r\n。，、！？!?.,;；：:、「」『』（）()「」\"'“”‘’"

    fun interpret(transcript: String): FormatCommandInterpretation {
        val normalized = normalize(transcript)
        return when {
            normalized == NEWLINE -> FormatCommandInterpretation.Newline
            normalized == BLANK_LINE -> FormatCommandInterpretation.BlankLine
            normalized in blockedCommands -> FormatCommandInterpretation.Unsupported
            else -> FormatCommandInterpretation.UseAi
        }
    }

    internal fun normalize(transcript: String): String = transcript.trim { it in TRIMMABLE }
}

/** 計算「空一行」實際要插入幾個換行，避免產生多餘空行。 */
internal object BlankLineInsertion {
    /** 無法安全取得前後文字時的明確降級行為：一段與前文分隔的空白行。 */
    const val FALLBACK = "\n\n"

    private const val TARGET_NEWLINES = 2
    private const val MAX_LOOKAROUND = 2

    fun newlinesFor(before: String?, after: String?, canInspect: Boolean): String {
        if (!canInspect || before == null || after == null) return FALLBACK
        val existing = trailingNewlines(before) + leadingNewlines(after)
        val needed = (TARGET_NEWLINES - existing).coerceAtLeast(0)
        return "\n".repeat(needed)
    }

    private fun trailingNewlines(text: String): Int =
        text.reversed().takeWhile { it == '\n' }.count().coerceAtMost(MAX_LOOKAROUND)

    private fun leadingNewlines(text: String): Int =
        text.takeWhile { it == '\n' }.count().coerceAtMost(MAX_LOOKAROUND)
}

/**
 * 第二層：格式指令專用的 AI Prompt。
 * 與一般文字整理分開，但共用同一個 Provider Client、API Key 與逾時／取消機制，
 * 且在單次格式指令中只送出一次請求。
 */
internal object FormatCommandPrompt {
    const val FORMAT_COMMAND_HINT = "可說：換行、空一行、列成三點、加入標題"

    val SYSTEM_PROMPT = """
        你是語音輸入法的純文字排版工具。

        輸入是使用者在「格式指令模式」說出的一句話，可能包含排版指令，也可能是帶有格式要求的內容。
        你只負責文字與排版，不得執行任何實際操作。

        直接輸出要插入輸入欄位的純文字，不要任何前言、說明、註解或總結。

        支援的排版：
        - 換行：輸出一個換行。
        - 空一行：輸出一個空白行。
        - 編號清單：每項一行，依序使用 1. 2. 3.
        - 項目符號：每項一行，使用 •
        - 標題：第一行輸出標題文字，接著輸出一個空白行，再輸出後續內容。
        - 混合指令：依使用者說出的順序組合成一段純文字。

        規則：
        - 只保留使用者說出的內容與順序，不增加、不刪除、不改寫、不補充說明。
        - 不產生使用者沒有說出的項目或內容。
        - 保留技術名詞、英文、數字、型號與代號的原拼法與大小寫。
        - 保留中文與英文的自然混合。
        - 不使用 Markdown 語法，不可使用 #、**、`、表格或程式碼區塊。
        - 只允許純文字、換行，以及 1. 2. 3. 或 • 的簡單列點。
        - 沒有排版指令時，直接輸出該段內容。
        - 不回答問題、不提供建議、不改變說話者原意。
        - 有歧義時保留原文，不要自行猜測格式。
        - 使用繁體中文。

        禁止：
        - 「刪除」「清空」「清除」「送出」「傳送」「重新命名」等要求都不是排版指令，不要執行，也不要輸出任何文字。
        - 如果這句話無法用純文字排版表達，輸出空字串。

        輸出必須只包含處理後的純文字。
    """.trimIndent()
}
