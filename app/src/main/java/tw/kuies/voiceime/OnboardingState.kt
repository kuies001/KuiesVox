package tw.kuies.voiceime

import android.content.Context
import java.io.IOException

/**
 * 首次安裝導覽的完成狀態儲存抽象；抽出來讓「首次顯示／完成後不再顯示」可在 JVM 測試中驗證。
 */
internal interface OnboardingFlagStore {
    fun isCompleted(): Boolean

    fun markCompleted()
}

/**
 * 導覽顯示判斷：只看「是否已完成」，不看版本號。
 *
 * 因此完成（或略過）之後，即使之後升級版本也不會再自動顯示；從設定頁重新查看時也不會把
 * 狀態改回未完成。
 */
internal object OnboardingGate {
    fun shouldShowOnStartup(store: OnboardingFlagStore): Boolean = !store.isCompleted()

    fun complete(store: OnboardingFlagStore) {
        store.markCompleted()
    }
}

/** 以 SharedPreferences 保存單一布林值；預設 false，代表首次安裝。 */
internal class SharedPreferencesOnboardingFlagStore(context: Context) : OnboardingFlagStore {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun isCompleted(): Boolean = preferences.getBoolean(COMPLETED_KEY, false)

    override fun markCompleted() {
        val saved = preferences.edit().putBoolean(COMPLETED_KEY, true).commit()
        if (!saved) throw IOException("Onboarding state could not be persisted")
    }

    companion object {
        const val PREFERENCES_NAME = "onboarding_state"
        const val COMPLETED_KEY = "onboarding_completed"
    }
}

/** 應用程式層級的入口：讀取為單一布林值，寫入走既有的儲存執行緒。 */
internal object OnboardingStateRepository {
    fun shouldShowOnStartup(context: Context): Boolean =
        OnboardingGate.shouldShowOnStartup(SharedPreferencesOnboardingFlagStore(context))

    fun markCompleted(context: Context, callback: (Result<Unit>) -> Unit = {}) =
        AppStorageExecutor.submit(
            { OnboardingGate.complete(SharedPreferencesOnboardingFlagStore(context)) },
            callback
        )
}
