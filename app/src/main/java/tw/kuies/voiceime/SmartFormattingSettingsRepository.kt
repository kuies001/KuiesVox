package tw.kuies.voiceime

import android.content.Context
import java.io.IOException

internal object SmartFormattingSettingsRepository {
    private const val PREFERENCES_NAME = "smart_formatting_settings"
    private const val ENABLED_KEY = "smart_formatting_enabled"
    private const val THRESHOLD_KEY = "smart_formatting_threshold"
    private const val MODEL_KEY = "smart_formatting_model"

    fun loadAsync(context: Context, callback: (Result<SmartFormattingSettings>) -> Unit) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({ loadSync(appContext) }, callback)
    }

    fun saveAsync(
        context: Context,
        settings: SmartFormattingSettings,
        callback: (Result<Unit>) -> Unit
    ) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({
            saveSync(appContext, settings)
            Unit
        }, callback)
    }

    internal fun loadSync(context: Context): SmartFormattingSettings {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return normalize(
            SmartFormattingSettings(
                enabled = preferences.getBoolean(ENABLED_KEY, SmartFormattingSettings.DEFAULT_ENABLED),
                threshold = preferences.getInt(THRESHOLD_KEY, SmartFormattingSettings.DEFAULT_THRESHOLD),
                model = preferences.getString(MODEL_KEY, SmartFormattingSettings.DEFAULT_MODEL).orEmpty()
            )
        )
    }

    internal fun saveSync(context: Context, settings: SmartFormattingSettings) {
        val normalized = normalize(settings)
        val saved = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED_KEY, normalized.enabled)
            .putInt(THRESHOLD_KEY, normalized.threshold)
            .putString(MODEL_KEY, normalized.model)
            .commit()
        if (!saved) throw IOException("Smart formatting settings could not be persisted")
    }

    internal fun normalize(settings: SmartFormattingSettings): SmartFormattingSettings = settings.copy(
        threshold = settings.threshold.coerceIn(
            SmartFormattingSettings.MIN_THRESHOLD,
            SmartFormattingSettings.MAX_THRESHOLD
        ),
        model = settings.model.trim().ifBlank { SmartFormattingSettings.DEFAULT_MODEL }
    )
}
