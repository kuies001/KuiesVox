package tw.kuies.voiceime

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import kotlin.math.abs

/** 用來判定「全新安裝 vs 既有安裝」的訊號；由 [ThemePreferenceStore] 的實作取得。 */
internal data class ThemeInstallSignals(
    val onboardingCompleted: Boolean,
    val firstInstallTimeMillis: Long,
    val lastUpdateTimeMillis: Long,
    val nowMillis: Long
)

/**
 * 主題預設值判定。
 *
 * 刻意不以「SharedPreferences key 不存在」判定首次安裝：升級安裝同樣沒有這個 key，
 * 若照此判定會讓既有使用者的外觀被換掉。這裡改用兩個既有訊號：
 *  1. 既有的首次安裝導覽狀態（`onboarding_state`）——完成過導覽就一定不是新使用者。
 *  2. 套件首次安裝時間與最後更新時間是否落在同一段短時間內——只有全新安裝才會如此。
 * 兩者都指向「新安裝」時才採用新預設（[AppThemeId.DEFAULT_FOR_NEW_INSTALL]）。
 */
internal object ThemeDefaultPolicy {
    /** 全新安裝的判定視窗；首次安裝與更新時間落在這之內視為同一次安裝。 */
    const val FRESH_INSTALL_WINDOW_MS = 10 * 60 * 1000L

    fun isFreshInstall(signals: ThemeInstallSignals): Boolean {
        if (signals.onboardingCompleted) return false
        if (signals.firstInstallTimeMillis <= 0L || signals.lastUpdateTimeMillis <= 0L) return false
        // 裝置時間異常（首次安裝時間在未來）時保守處理，一律視為既有安裝。
        if (signals.firstInstallTimeMillis > signals.nowMillis) return false
        val ageOfFirstInstall = signals.nowMillis - signals.firstInstallTimeMillis
        val installAndUpdateTogether =
            abs(signals.lastUpdateTimeMillis - signals.firstInstallTimeMillis) <= FRESH_INSTALL_WINDOW_MS
        return ageOfFirstInstall <= FRESH_INSTALL_WINDOW_MS && installAndUpdateTogether
    }

    /** 未知或損毀的 ID 一律安全 fallback：新安裝用新預設，既有安裝維持原貌。 */
    fun resolveThemeId(storedRawId: String?, signals: ThemeInstallSignals): AppThemeId =
        AppThemeId.fromStableId(storedRawId)
            ?: if (isFreshInstall(signals)) {
                AppThemeId.DEFAULT_FOR_NEW_INSTALL
            } else {
                AppThemeId.DEFAULT_FOR_EXISTING_INSTALL
            }

    fun resolveAppearance(storedRaw: String?): ThemeAppearance =
        ThemeAppearance.fromStableId(storedRaw) ?: ThemeAppearance.DEFAULT
}

/** 使用者目前的主題偏好。 */
internal data class ThemeSelection(
    val themeId: AppThemeId,
    val appearance: ThemeAppearance
) {
    val themeStableId: String get() = themeId.stableId
    val appearanceStableId: String get() = appearance.stableId
}

/**
 * 主題偏好的儲存抽象。抽出來讓「預設值／遷移／持久化／變更通知」可以在 JVM 測試中驗證，
 * 與既有的 `OnboardingFlagStore` 同一種做法。
 */
internal interface ThemePreferenceStore {
    fun readThemeId(): String?

    fun readAppearanceMode(): String?

    /** 只寫入主題相關的 key；回傳是否成功。 */
    fun writeSelection(selection: ThemeSelection): Boolean

    fun readInstallSignals(): ThemeInstallSignals

    /** 註冊變更通知（同一個 process 內），回傳取消註冊的動作。 */
    fun registerChangeListener(listener: () -> Unit): () -> Unit
}

/**
 * 主題偏好的唯一儲存來源，App 設定畫面與 IME Service 共用同一份。
 *
 * 需求對應：
 *  - 重啟仍有效：寫入專用 SharedPreferences（[SharedPreferencesThemeStore.PREFERENCES_NAME]）。
 *  - IME 不依賴 Activity：只使用 application context。
 *  - 同步通知：儲存層發出變更通知，物件層擁有記憶體快取，因此每個音量動畫影格都不需要讀檔案。
 *  - 不影響其他設定：只寫自己的檔案與 key。
 */
internal class ThemePreferences(private val store: ThemePreferenceStore) {
    @Volatile
    private var cachedSelection: ThemeSelection? = null

    /**
     * 讀取（必要時套用預設並寫回）。第一次解析後即成為既有的持久化偏好，
     * 因此「新安裝／既有安裝」的判斷只會發生一次。
     */
    fun load(): ThemeSelection {
        val storedThemeId = store.readThemeId()
        val storedAppearance = store.readAppearanceMode()

        val validThemeId = AppThemeId.fromStableId(storedThemeId)
        val themeId = validThemeId ?: run {
            // 只有在沒有可用偏好時才需要安裝訊號（會查 PackageManager），避免常態負擔。
            val signals = store.readInstallSignals()
            ThemeDefaultPolicy.resolveThemeId(storedThemeId, signals)
        }
        val validAppearance = ThemeAppearance.fromStableId(storedAppearance)
        val selection = ThemeSelection(
            themeId = themeId,
            appearance = validAppearance ?: ThemeDefaultPolicy.resolveAppearance(storedAppearance)
        )

        // 一次性的遷移寫回：寫入後即成為穩定偏好，之後不再依安裝訊號判斷。
        if (validThemeId == null || validAppearance == null) {
            store.writeSelection(selection)
        }
        cachedSelection = selection
        return selection
    }

    /** 已有快取時不重讀儲存層；供 IME 與高頻路徑使用。 */
    fun cached(): ThemeSelection = cachedSelection ?: load()

    fun save(selection: ThemeSelection) {
        if (!store.writeSelection(selection)) {
            throw IOException("Theme preferences could not be saved")
        }
        cachedSelection = selection
    }

    fun setThemeId(themeId: AppThemeId): ThemeSelection =
        load().copy(themeId = themeId).also(::save)

    fun setAppearance(appearance: ThemeAppearance): ThemeSelection =
        load().copy(appearance = appearance).also(::save)

    /** 註冊變更監聽：註冊時先送出目前值，之後每次寫入都會回呼。 */
    fun observe(listener: (ThemeSelection) -> Unit): () -> Unit {
        // 先讀一次（必要時寫回遷移值）再註冊，避免第一次的遷移寫入造成立即重複回呼。
        val current = load()
        val unsubscribe = store.registerChangeListener { listener(load()) }
        listener(current)
        return unsubscribe
    }
}

/** 以 SharedPreferences 保存主題偏好；只碰自己的檔案與兩個 key。 */
internal class SharedPreferencesThemeStore(context: Context) : ThemePreferenceStore {
    private val applicationContext = context.applicationContext
    private val preferences =
        applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun readThemeId(): String? = preferences.getString(THEME_ID_KEY, null)

    override fun readAppearanceMode(): String? = preferences.getString(APPEARANCE_KEY, null)

    override fun writeSelection(selection: ThemeSelection): Boolean =
        preferences.edit()
            .putString(THEME_ID_KEY, selection.themeStableId)
            .putString(APPEARANCE_KEY, selection.appearanceStableId)
            .commit()

    override fun readInstallSignals(): ThemeInstallSignals {
        var firstInstallTime = 0L
        var lastUpdateTime = 0L
        runCatching {
            val info = applicationContext.packageManager
                .getPackageInfo(applicationContext.packageName, 0)
            firstInstallTime = info.firstInstallTime
            lastUpdateTime = info.lastUpdateTime
        }
        return ThemeInstallSignals(
            onboardingCompleted = !OnboardingStateRepository.shouldShowOnStartup(applicationContext),
            firstInstallTimeMillis = firstInstallTime,
            lastUpdateTimeMillis = lastUpdateTime,
            nowMillis = System.currentTimeMillis()
        )
    }

    override fun registerChangeListener(listener: () -> Unit): () -> Unit {
        val sharedListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == THEME_ID_KEY || key == APPEARANCE_KEY) listener()
        }
        preferences.registerOnSharedPreferenceChangeListener(sharedListener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(sharedListener) }
    }

    companion object {
        const val PREFERENCES_NAME = "theme_preferences"
        const val THEME_ID_KEY = "theme_id"
        const val APPEARANCE_KEY = "appearance_mode"
    }
}

/**
 * App／IME 共用的入口。整份儲存與快取在同一個 process 內只有一份實例，
 * 因此設定畫面與輸入法會看到同一個主題，也只需要讀一次檔案。
 */
internal object ThemePreferencesRepository {
    @Volatile
    private var instance: ThemePreferences? = null

    fun of(context: Context): ThemePreferences {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: ThemePreferences(SharedPreferencesThemeStore(context.applicationContext))
                .also { instance = it }
        }
    }

    fun loadSync(context: Context): ThemeSelection = of(context).load()

    fun cachedSync(context: Context): ThemeSelection = of(context).cached()

    fun saveSync(context: Context, selection: ThemeSelection) = of(context).save(selection)

    fun load(context: Context, callback: (Result<ThemeSelection>) -> Unit) =
        AppStorageExecutor.submit({ loadSync(context) }, callback)

    fun save(context: Context, selection: ThemeSelection, callback: (Result<Unit>) -> Unit = {}) =
        AppStorageExecutor.submit({ saveSync(context, selection) }, callback)

    fun setThemeId(
        context: Context,
        themeId: AppThemeId,
        callback: (Result<ThemeSelection>) -> Unit = {}
    ) = AppStorageExecutor.submit({ of(context).setThemeId(themeId) }, callback)

    fun setAppearance(
        context: Context,
        appearance: ThemeAppearance,
        callback: (Result<ThemeSelection>) -> Unit = {}
    ) = AppStorageExecutor.submit({ of(context).setAppearance(appearance) }, callback)

    fun registerListener(context: Context, listener: (ThemeSelection) -> Unit): () -> Unit =
        of(context).observe(listener)
}
