package tw.kuies.voiceime

import android.content.Context
import java.io.IOException

/**
 * 個人常用詞的本機儲存。
 *
 * 讀取只還原使用者自己存放的內容（[PersonalGlossaryCodec]），不再有任何內建預設或
 * 版本遷移植入，因此新安裝一定是空詞庫，覆蓋安裝也不會動到既有資料。
 */
internal object PersonalGlossaryRepository {
    private const val PREFERENCES_NAME = "personal_glossary"
    private const val ENTRIES_KEY = "entries_v1"

    fun load(
        context: Context,
        callback: (Result<List<PersonalGlossaryTerm>>) -> Unit
    ) {
        submit(callback) { readEntries(context.applicationContext) }
    }

    fun add(
        context: Context,
        rawTerms: List<String>,
        callback: (Result<PersonalGlossaryAddResult>) -> Unit
    ) {
        submit(callback) {
            val appContext = context.applicationContext
            val currentEntries = readEntries(appContext)
            val result = PersonalGlossaryRules.add(currentEntries, rawTerms)
            if (result.addedCount > 0) persistEntries(appContext, result.entries)
            result
        }
    }

    fun setEnabled(
        context: Context,
        id: String,
        enabled: Boolean,
        callback: (Result<List<PersonalGlossaryTerm>>) -> Unit
    ) {
        update(context, callback) { entries ->
            PersonalGlossaryRules.setEnabled(entries, id, enabled)
        }
    }

    fun setCommonPhrases(
        context: Context,
        id: String,
        phrases: List<String>,
        callback: (Result<List<PersonalGlossaryTerm>>) -> Unit
    ) {
        update(context, callback) { entries ->
            PersonalGlossaryRules.setCommonPhrases(entries, id, phrases)
        }
    }

    fun delete(
        context: Context,
        id: String,
        callback: (Result<List<PersonalGlossaryTerm>>) -> Unit
    ) {
        update(context, callback) { entries ->
            PersonalGlossaryRules.delete(entries, id)
        }
    }

    /**
     * 批次刪除選取的詞彙。
     *
     * 在同一個儲存作業內重新讀取最新資料後只依穩定 ID 移除，因此：只寫入一次、不會用舊清單
     * 覆寫期間新增或編輯的內容、未選取的項目（含啟用狀態與語境句）完全不受影響；找不到的 ID
     * 直接忽略，所以重複送出不會再刪掉任何東西。
     */
    fun deleteSelected(
        context: Context,
        ids: Collection<String>,
        callback: (Result<List<PersonalGlossaryTerm>>) -> Unit
    ) {
        if (ids.isEmpty()) {
            load(context, callback)
            return
        }
        update(context, callback) { entries ->
            PersonalGlossaryRules.deleteAll(entries, ids)
        }
    }

    private fun update(
        context: Context,
        callback: (Result<List<PersonalGlossaryTerm>>) -> Unit,
        transform: (List<PersonalGlossaryTerm>) -> List<PersonalGlossaryTerm>
    ) {
        submit(callback) {
            val appContext = context.applicationContext
            val currentEntries = readEntries(appContext)
            val updatedEntries = transform(currentEntries)
            if (updatedEntries != currentEntries) persistEntries(appContext, updatedEntries)
            updatedEntries
        }
    }

    private fun <T> submit(
        callback: (Result<T>) -> Unit,
        operation: () -> T
    ) {
        AppStorageExecutor.submit(operation, callback)
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun readEntries(context: Context): List<PersonalGlossaryTerm> =
        PersonalGlossaryCodec.decode(preferences(context).getString(ENTRIES_KEY, null))

    private fun persistEntries(context: Context, entries: List<PersonalGlossaryTerm>) {
        val saved = preferences(context)
            .edit()
            .putString(ENTRIES_KEY, PersonalGlossaryCodec.encode(entries))
            .commit()
        if (!saved) throw IOException("Personal glossary could not be persisted")
    }
}
