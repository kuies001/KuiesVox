package tw.kuies.voiceime

/**
 * 共用的 AI 文字轉換核心。
 *
 * 本階段只實作 [TextTransformationType.EDIT_SELECTED_TEXT]，
 * 但請求／結果型別刻意設計成語音翻譯與剪貼簿加工可以直接沿用，
 * 不必再新增另一套 Provider 呼叫與錯誤處理。
 */
internal enum class TextTransformationType {
    EDIT_SELECTED_TEXT,
    TRANSLATE_SPEECH,
    TRANSFORM_CLIPBOARD
}

internal data class TextTransformationRequest(
    val transformationType: TextTransformationType,
    val sourceText: String,
    val instruction: String,
    val provider: TextFormattingProviderId,
    val model: String,
    val operationId: Long,
    /** 語音翻譯使用：目標語言名稱；其他型別為 null。 */
    val targetLanguage: String? = null
)

internal sealed interface TextTransformationResult {
    data class Success(
        val transformedText: String,
        val operationId: Long
    ) : TextTransformationResult

    data class Failure(
        val kind: TextTransformationFailure,
        val operationId: Long
    ) : TextTransformationResult
}

internal enum class TextTransformationFailure {
    /** AI 回傳空白：不得因此刪除原文。 */
    EMPTY_RESULT,
    PROVIDER_UNAVAILABLE,
    REQUEST_FAILED,
    SOURCE_TOO_LONG,
    UNSUPPORTED_INSTRUCTION
}

/** 一次轉換要送出的 prompt（system + user），由 [TextTransformationCore] 組裝。 */
internal data class TextTransformationPrompt(
    val systemPrompt: String,
    val userMessage: String
)

internal object TextTransformationCore {
    fun promptFor(request: TextTransformationRequest): TextTransformationPrompt =
        when (request.transformationType) {
            TextTransformationType.EDIT_SELECTED_TEXT -> TextTransformationPrompt(
                systemPrompt = TextEditPrompt.SYSTEM_PROMPT,
                userMessage = TextEditPrompt.buildUserMessage(
                    instruction = request.instruction,
                    sourceContent = request.sourceText
                )
            )
            TextTransformationType.TRANSLATE_SPEECH -> TextTransformationPrompt(
                systemPrompt = TextTranslatePrompt.SYSTEM_PROMPT,
                userMessage = TextTranslatePrompt.buildUserMessage(
                    sourceContent = request.sourceText,
                    targetLanguage = request.targetLanguage.orEmpty()
                )
            )
            // 尚未開發的模式不得悄悄使用編輯 prompt。
            TextTransformationType.TRANSFORM_CLIPBOARD -> TextTransformationPrompt(
                systemPrompt = TextEditPrompt.SYSTEM_PROMPT,
                userMessage = TextEditPrompt.buildUserMessage(
                    instruction = request.instruction,
                    sourceContent = request.sourceText
                )
            )
        }

    /** AI 回傳的內容一律以空白視為失敗，避免用空字串覆蓋原文。 */
    fun interpretSuccess(request: TextTransformationRequest, transformedText: String?): TextTransformationResult {
        val trimmed = transformedText?.trim().orEmpty()
        return if (trimmed.isEmpty()) {
            TextTransformationResult.Failure(
                TextTransformationFailure.EMPTY_RESULT,
                request.operationId
            )
        } else {
            TextTransformationResult.Success(trimmed, request.operationId)
        }
    }

    fun interpretFailure(
        request: TextTransformationRequest,
        failureType: String? = null
    ): TextTransformationResult = TextTransformationResult.Failure(
        kind = if (failureType == "empty_result") {
            TextTransformationFailure.EMPTY_RESULT
        } else {
            TextTransformationFailure.REQUEST_FAILED
        },
        operationId = request.operationId
    )
}
