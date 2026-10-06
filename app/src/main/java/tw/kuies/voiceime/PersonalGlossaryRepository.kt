package tw.kuies.voiceime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

internal object PersonalGlossaryRepository {
    private const val PREFERENCES_NAME = "personal_glossary"
    private const val ENTRIES_KEY = "entries_v1"
    private const val DEFAULT_DATA_VERSION_KEY = "default_data_version"

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

    fun importDefaults(
        context: Context,
        callback: (Result<PersonalGlossaryAddResult>) -> Unit
    ) {
        submit(callback) {
            val appContext = context.applicationContext
            val currentEntries = readStoredEntries(appContext)
            val result = DefaultGlossaryData.importMissing(currentEntries)
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

    fun delete(
        context: Context,
        id: String,
        callback: (Result<List<PersonalGlossaryTerm>>) -> Unit
    ) {
        update(context, callback) { entries ->
            PersonalGlossaryRules.delete(entries, id)
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

    private fun readEntries(context: Context): List<PersonalGlossaryTerm> {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val storedVersion = preferences.getInt(DEFAULT_DATA_VERSION_KEY, 0)
        val entries = readStoredEntries(context)

        val migration = DefaultGlossaryData.migrate(entries, storedVersion)
        if (migration.version != storedVersion) {
            persistEntries(context, migration.values, migration.version)
        }
        return migration.values
    }

    private fun readStoredEntries(context: Context): List<PersonalGlossaryTerm> {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val storedJson = preferences.getString(ENTRIES_KEY, null)

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
                enabled = jsonEntry.optBoolean("enabled", true)
            )
        }

        return entries
    }

    private fun persistEntries(
        context: Context,
        entries: List<PersonalGlossaryTerm>,
        defaultDataVersion: Int? = null
    ) {
        val jsonEntries = JSONArray()
        entries.forEach { entry ->
            jsonEntries.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("term", entry.term)
                    .put("enabled", entry.enabled)
            )
        }

        val editor = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(ENTRIES_KEY, jsonEntries.toString())
        defaultDataVersion?.let { editor.putInt(DEFAULT_DATA_VERSION_KEY, it) }
        val saved = editor.commit()
        if (!saved) throw IOException("Personal glossary could not be persisted")
    }
}
