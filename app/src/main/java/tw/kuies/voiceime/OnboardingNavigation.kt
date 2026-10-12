package tw.kuies.voiceime

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/** 導覽步驟；索引與 `OnboardingScreen` 的頁面順序一致。 */
internal enum class OnboardingStep(val index: Int) {
    MICROPHONE(0),
    GROQ(1),
    IME(2);

    companion object {
        fun fromIndex(index: Int): OnboardingStep =
            entries.firstOrNull { it.index == index } ?: MICROPHONE
    }
}

/** 這次導覽是首次啟動自動顯示，還是使用者從設定頁主動重新查看。 */
internal enum class OnboardingEntryPoint { FIRST_RUN, SETTINGS_REVIEW }

/**
 * 新手導覽的明確導航狀態。
 *
 * 不再用單一布林值同時代表「是否顯示導覽」「是否已完成」「是否正在設定 API Key」：
 * 由導覽進入 Groq 設定頁時導覽仍然存在，只是暫時顯示 Groq 頁面，因此返回與儲存結果
 * 都能回到正確的導覽頁面。
 */
internal data class OnboardingNavigationState(
    val isActive: Boolean,
    val entryPoint: OnboardingEntryPoint,
    val step: OnboardingStep,
    /** 目前顯示的 Groq 設定頁是由導覽流程進入的。 */
    val groqSettingsForOnboarding: Boolean,
    /** 已送出 API Key 儲存，正在等待非同步結果。 */
    val awaitingApiKeySave: Boolean
) {
    /** 導覽畫面本身是否應該顯示（由導覽進入 Groq 設定時改由設定頁顯示）。 */
    val showsOnboardingScreen: Boolean get() = isActive && !groqSettingsForOnboarding
}

/** 導覽狀態轉移；全部為純函式，因此可在 JVM 測試中完整驗證。 */
internal object OnboardingNavigation {

    fun start(entryPoint: OnboardingEntryPoint): OnboardingNavigationState =
        OnboardingNavigationState(
            isActive = true,
            entryPoint = entryPoint,
            step = OnboardingStep.MICROPHONE,
            groqSettingsForOnboarding = false,
            awaitingApiKeySave = false
        )

    fun idle(): OnboardingNavigationState = OnboardingNavigationState(
        isActive = false,
        entryPoint = OnboardingEntryPoint.FIRST_RUN,
        step = OnboardingStep.MICROPHONE,
        groqSettingsForOnboarding = false,
        awaitingApiKeySave = false
    )

    fun goToStep(state: OnboardingNavigationState, step: OnboardingStep): OnboardingNavigationState =
        state.copy(step = step)

    /**
     * 由導覽第二頁進入 Groq API 設定：保留導覽流程，只切換顯示的頁面。
     * 這裡刻意不標記完成，使用者還沒完成導覽。
     *
     * 導覽未開啟時（一般設定頁進入 Groq）維持原狀，否則返回鍵會被誤判成
     * 「回到導覽第二頁」，等於從設定頁觸發導覽。
     */
    fun openGroqFromOnboarding(state: OnboardingNavigationState): OnboardingNavigationState =
        if (state.isActive) {
            state.copy(groqSettingsForOnboarding = true, awaitingApiKeySave = false)
        } else {
            state
        }

    /**
     * 從 Groq 設定頁返回。
     * 由導覽進入時回到導覽第二頁（繼續／略過選項都還在）；其餘情況回傳 null，
     * 代表交還給一般設定頁的返回邏輯（回首頁），不觸發導覽。
     */
    fun backFromGroq(state: OnboardingNavigationState): OnboardingNavigationState? =
        if (state.groqSettingsForOnboarding) {
            state.copy(groqSettingsForOnboarding = false, awaitingApiKeySave = false)
        } else {
            null
        }

    /** 使用者按下儲存：開始等待非同步結果。 */
    fun onApiKeySaveStarted(state: OnboardingNavigationState): OnboardingNavigationState =
        if (state.groqSettingsForOnboarding) state.copy(awaitingApiKeySave = true) else state

    /**
     * 儲存成功：由導覽進入時自動回到導覽並前進到第三頁「啟用 KuiesVox」，
     * 使用者不需要手動按返回。由一般設定頁進入時不改變導覽狀態。
     */
    fun onApiKeySaveSucceeded(state: OnboardingNavigationState): OnboardingNavigationState =
        if (state.groqSettingsForOnboarding) {
            state.copy(
                step = OnboardingStep.IME,
                groqSettingsForOnboarding = false,
                awaitingApiKeySave = false
            )
        } else {
            state
        }

    /**
     * 儲存失敗：留在 Groq 設定頁、顯示錯誤，不跳頁，也不清掉已輸入的 Key。
     */
    fun onApiKeySaveFailed(state: OnboardingNavigationState): OnboardingNavigationState =
        if (state.groqSettingsForOnboarding) state.copy(awaitingApiKeySave = false) else state

    /** 完成或略過導覽：導覽結束，這才是唯一可以標記 onboarding_completed 的時機。 */
    fun finish(state: OnboardingNavigationState): OnboardingNavigationState = idle()

    /** 只有真正完成或略過導覽才標記完成；開啟 Groq 設定、切換頁面都不算。 */
    fun shouldMarkCompleted(state: OnboardingNavigationState): Boolean = !state.isActive
}

/** 輸入法啟用狀態。 */
internal enum class ImeEnablement {
    /** KuiesVox 不在已啟用輸入法清單中。 */
    NOT_ENABLED,

    /** 已啟用但不是目前的預設輸入法。 */
    ENABLED,

    /** 已啟用且是目前的預設輸入法。 */
    DEFAULT
}

/**
 * 輸入法狀態來源；抽出來讓判定邏輯可在 JVM 測試中驗證，
 * 也避免把 InputMethodManager 的細節散落在 Compose 畫面裡。
 */
internal interface ImeStateSource {
    /** `InputMethodManager.getEnabledInputMethodList()` 的 id 清單。 */
    fun enabledInputMethodIds(): List<String>

    /** 系統目前的預設輸入法 id（`Settings.Secure.DEFAULT_INPUT_METHOD`）。 */
    fun defaultInputMethodId(): String?
}

/**
 * 以官方 API 判斷 KuiesVox 是否已啟用／已設為預設。
 *
 * 只比對輸入法 id，且把 `pkg/.Cls` 與 `pkg/pkg.Cls` 兩種寫法正規化後再比，
 * 不與其他輸入法混淆；也不以「設定頁是否開啟成功」推測已啟用。
 */
internal object ImeEnablementPolicy {
    fun evaluate(
        enabledInputMethodIds: List<String>,
        defaultInputMethodId: String?,
        targetPackage: String,
        targetClassName: String
    ): ImeEnablement {
        val target = canonicalId(targetPackage, targetClassName)
        val enabled = enabledInputMethodIds.map { canonicalize(it) }
        if (target !in enabled) return ImeEnablement.NOT_ENABLED
        val default = defaultInputMethodId?.let { canonicalize(it) }
        return if (default == target) ImeEnablement.DEFAULT else ImeEnablement.ENABLED
    }

    /** 把各種合法的 ComponentName 寫法統一成 `package/fully.qualified.Class`。 */
    fun canonicalize(inputMethodId: String): String {
        val trimmed = inputMethodId.trim()
        if (trimmed.isEmpty()) return ""
        val separator = trimmed.indexOf('/')
        if (separator <= 0) return trimmed
        val packageName = trimmed.substring(0, separator)
        val className = trimmed.substring(separator + 1)
        return canonicalId(packageName, className)
    }

    private fun canonicalId(packageName: String, className: String): String {
        val name = className.trim()
        val qualified = when {
            name.isEmpty() -> name
            name.startsWith(".") -> packageName + name
            name.contains('.') -> name
            else -> "$packageName.$name"
        }
        return "$packageName/$qualified"
    }
}

/** Android 實作：只讀取已啟用清單與系統預設輸入法，不改動任何設定。 */
internal class AndroidImeStateSource(private val context: Context) : ImeStateSource {
    override fun enabledInputMethodIds(): List<String> {
        val manager = context.getSystemService(InputMethodManager::class.java) ?: return emptyList()
        return manager.enabledInputMethodList.map { it.id }
    }

    override fun defaultInputMethodId(): String? =
        runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        }.getOrNull()

    companion object {
        /** 開啟 Android 的輸入法設定頁（唯讀查詢之外的唯一動作）。 */
        fun openInputMethodSettings(context: Context): Boolean = runCatching {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            true
        }.getOrDefault(false)
    }
}
