package tw.kuies.voiceime

/**
 * 工具列「切換鍵盤」按鈕點擊後要走哪條路（純邏輯，可用 JVM 單元測試驗證）。
 *
 * 按鈕一律可見；`InputMethodService.shouldOfferSwitchingToNextInputMethod()` 只代表
 * Android 是否適合直接切換下一個輸入法，會隨裝置已啟用的輸入法與子類型設定而不同，
 * 因此不能再用來決定按鈕要顯示或隱藏。
 */
internal enum class KeyboardSwitchRoute {
    /** 請 Android 直接切換到下一個輸入法。 */
    SWITCH_TO_NEXT,

    /** 開啟 Android 系統輸入法選擇器，由使用者自己選。 */
    SHOW_SYSTEM_PICKER
}

/** 點擊鍵盤按鈕後實際的結果。 */
internal enum class KeyboardSwitchOutcome {
    /** 已直接切換到下一個輸入法。 */
    SWITCHED_TO_NEXT,

    /** 已請系統開啟輸入法選擇器。 */
    SYSTEM_PICKER_REQUESTED,

    /** 直接切換與選擇器都無法使用；呼叫端可視情況顯示簡短提示。 */
    UNAVAILABLE
}

/**
 * Android 端實際可用的兩個官方動作。抽成介面後，切換決策與例外回退都能在 JVM 測試中
 * 以假物件驗證，不必碰 Android Framework，也讓「只用這兩個 API」變成可測試的約束。
 *
 * 兩者在 minSdk 26 都已存在（`switchToNextInputMethod` API 16+、
 * `showInputMethodPicker` API 3+），因此不需要版本判斷，也不需要任何權限。
 */
internal interface KeyboardSwitchHost {
    /** `InputMethodService.switchToNextInputMethod(false)`：是否真的完成切換。 */
    fun switchToNextInputMethod(): Boolean

    /** `InputMethodManager.showInputMethodPicker()`：是否已送出顯示請求。 */
    fun showSystemInputMethodPicker(): Boolean
}

/** 只依「Android 是否允許直接切換」決定路徑。 */
internal object KeyboardSwitchPolicy {
    fun routeFor(canSwitchToNext: Boolean): KeyboardSwitchRoute =
        if (canSwitchToNext) {
            KeyboardSwitchRoute.SWITCH_TO_NEXT
        } else {
            KeyboardSwitchRoute.SHOW_SYSTEM_PICKER
        }
}

/**
 * 執行鍵盤切換；直接切換失敗（回傳 false 或丟出例外）時退回系統輸入法選擇器。
 *
 * 每一步都吸收例外，避免不同 Android 裝置的行為造成崩潰，也避免點擊後毫無反應。
 * 除了 [KeyboardSwitchHost] 的兩個動作之外不做任何事，因此不會影響錄音狀態或資料。
 */
internal class KeyboardSwitcher(private val host: KeyboardSwitchHost) {
    fun perform(canSwitchToNext: Boolean): KeyboardSwitchOutcome {
        if (KeyboardSwitchPolicy.routeFor(canSwitchToNext) == KeyboardSwitchRoute.SWITCH_TO_NEXT) {
            val switched = runCatching { host.switchToNextInputMethod() }.getOrDefault(false)
            if (switched) return KeyboardSwitchOutcome.SWITCHED_TO_NEXT
        }
        val requested = runCatching { host.showSystemInputMethodPicker() }.getOrDefault(false)
        return if (requested) {
            KeyboardSwitchOutcome.SYSTEM_PICKER_REQUESTED
        } else {
            KeyboardSwitchOutcome.UNAVAILABLE
        }
    }
}
