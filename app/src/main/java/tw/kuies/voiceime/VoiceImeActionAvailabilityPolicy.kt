package tw.kuies.voiceime

/**
 * 集中管理輸入法動作權限與可用性判定。
 *
 * 核心原則：
 * 1. 麥克風未就緒只限制真正需要錄音的動作，絕不連帶鎖住設定、剪貼簿、更多選單、全選/清除等功能。
 * 2. 終止狀態（SUCCESS、ERROR、CANCELLED、FORMATTING_FALLBACK）只作展示提示，不阻擋無關功能操作。
 * 3. 語音作業進行中（RECORDING、TRANSCRIBING、FORMATTING）禁止破壞當前作業或造成文字寫入競態的操作。
 */
internal object VoiceImeActionAvailabilityPolicy {

    /**
     * 是否處於正在進行的語音作業（錄音中、辨識中、LLM 整理中）。
     */
    fun isVoiceOperationInFlight(state: VoiceImeState): Boolean =
        state == VoiceImeState.RECORDING ||
            state == VoiceImeState.TRANSCRIBING ||
            state == VoiceImeState.FORMATTING

    /**
     * 是否處於終止提示狀態（成功、錯誤、取消、整理失敗）。
     */
    fun isTerminal(state: VoiceImeState): Boolean =
        state == VoiceImeState.SUCCESS ||
            state == VoiceImeState.FORMATTING_FALLBACK ||
            state == VoiceImeState.CANCELLED ||
            state == VoiceImeState.ERROR

    /**
     * 是否正在等待使用者確認（例如 AI 編輯修改預覽）。
     */
    fun isAwaitingConfirm(state: VoiceImeState): Boolean =
        state == VoiceImeState.AWAITING_CONFIRM

    /**
     * 工具列「設定」按鈕是否可操作：
     * 不依賴麥克風就緒狀態。待命、終止狀態均可直接進入。
     * 若語音作業進行中，由 Service 安全取消/完成當前作業後進入。
     */
    fun isSettingsAvailable(state: VoiceImeState): Boolean = true

    /**
     * 工具列「更多」選單按鈕是否可操作：
     * 不依賴麥克風就緒狀態。待命與終止狀態均可開啟。
     * 語音作業進行中及修改預覽時停用，避免打斷當前作業。
     */
    fun isMoreMenuAvailable(state: VoiceImeState): Boolean =
        !isVoiceOperationInFlight(state) && !isAwaitingConfirm(state)

    /**
     * 工具列「剪貼簿」歷史按鈕是否可操作：
     * 不依賴麥克風就緒狀態。待命與終止狀態均可開啟。
     * 語音作業進行中及修改預覽時停用，避免打斷錄音或產生文字寫入競態。
     */
    fun isClipboardHistoryAvailable(state: VoiceImeState): Boolean =
        !isVoiceOperationInFlight(state) && !isAwaitingConfirm(state)

    /**
     * 工具列「全選」與「清除」等文字操作是否可操作：
     * 不依賴麥克風就緒狀態。待命與終止狀態均可正常操作。
     * 語音作業進行中（避免文字寫入競態）及修改預覽時停用。
     */
    fun isBulkTextActionAvailable(state: VoiceImeState): Boolean =
        !isVoiceOperationInFlight(state) && !isAwaitingConfirm(state)

    /**
     * 工具列「AI 翻譯」快捷按鈕是否可操作：
     * 不依賴麥克風就緒狀態。待命與終止狀態可進入/切換。
     * 語音作業進行中及修改預覽時停用。
     */
    fun isTranslateShortcutAvailable(state: VoiceImeState): Boolean =
        !isVoiceOperationInFlight(state) && !isAwaitingConfirm(state)

    /**
     * 工具列「切換鍵盤」按鈕是否可操作：
     * 永遠可操作，不依賴麥克風就緒狀態。
     */
    fun isKeyboardSwitchAvailable(state: VoiceImeState): Boolean = true

    /**
     * 中央錄音按鈕是否可開始錄音：
     * 必須無正在進行的作業，且麥克風權限已取得。
     */
    fun canStartRecording(
        state: VoiceImeState,
        hasMicPermission: Boolean,
        activeOperationId: Long
    ): Boolean =
        hasMicPermission &&
            activeOperationId == 0L &&
            (state == VoiceImeState.IDLE || isTerminal(state))
}
