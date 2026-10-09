package tw.kuies.voiceime

/** Nullable values mean that the app inherits that setting from KuiesVox's existing global settings. */
internal data class AppVoiceProfile(
    val packageName: String,
    val enabled: Boolean = true,
    val speechModelOverride: String? = null,
    val speechLanguageModeOverride: SpeechLanguageMode? = null,
    val smartFormattingEnabledOverride: Boolean? = null,
    val providerOverride: TextFormattingProviderId? = null,
    val contextualCorrectionEnabledOverride: Boolean? = null,
    val taiwanWordingEnabledOverride: Boolean? = null,
    val smartPunctuationEnabledOverride: Boolean? = null,
    val terminalPeriodModeOverride: TerminalPeriodMode? = null,
    val formattingStyleOverride: TextFormattingStyle? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = createdAt
)

internal enum class TextFormattingStyle(
    val storageValue: String,
    val displayName: String,
    val promptInstruction: String
) {
    DAILY(
        "daily",
        "日常口語",
        "保留自然口語與說話者原有語氣，只做必要整理。"
    ),
    FORMAL(
        "formal",
        "正式",
        "使用清楚、正式的書面語氣，但不得改變原意、立場或說話者的禮貌程度。"
    ),
    TECHNICAL(
        "technical",
        "技術用語",
        "保留技術名詞、指令、識別字與原有大小寫；不要翻譯或改寫專有名詞。"
    );

    companion object {
        fun fromStoredValue(value: String?): TextFormattingStyle? =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) }
    }
}

internal object AppPackageName {
    private val validPackage = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+$")

    fun normalize(value: String?): String? = value?.trim()?.takeIf(validPackage::matches)
}

internal data class ResolvedAppVoiceSettings(
    val settings: SmartFormattingSettings,
    val formattingStyle: TextFormattingStyle,
    val appliedProfile: AppVoiceProfile?
)

internal object AppVoiceProfilePolicy {
    fun resolve(
        packageName: String?,
        profiles: List<AppVoiceProfile>,
        globalSettings: SmartFormattingSettings,
        sensitiveEditor: Boolean = false
    ): ResolvedAppVoiceSettings {
        if (sensitiveEditor) {
            return ResolvedAppVoiceSettings(
                settings = globalSettings.copy(
                    contextualCorrectionEnabled = false,
                    taiwanWordingEnabled = false,
                    smartPunctuationEnabled = false
                ),
                formattingStyle = globalSettings.formattingStyle,
                appliedProfile = null
            )
        }
        val normalizedPackage = AppPackageName.normalize(packageName)
        val profile = normalizedPackage?.let { packageValue ->
            profiles.lastOrNull { it.packageName == packageValue && it.enabled }
        }
        if (profile == null) {
            return ResolvedAppVoiceSettings(
                settings = globalSettings,
                formattingStyle = globalSettings.formattingStyle,
                appliedProfile = null
            )
        }

        val validSpeechModel = profile.speechModelOverride?.takeIf { model ->
            FormattingModels.speech.any { it.id == model }
        }
        val validProvider = profile.providerOverride?.takeIf { provider ->
            TextFormattingProviderRegistry.isProfileSupported(provider)
        }
        val settings = globalSettings.copy(
            speechModel = validSpeechModel ?: globalSettings.speechModel,
            speechLanguageMode = profile.speechLanguageModeOverride ?: globalSettings.speechLanguageMode,
            enabled = profile.smartFormattingEnabledOverride ?: globalSettings.enabled,
            provider = validProvider ?: globalSettings.provider,
            contextualCorrectionEnabled = profile.contextualCorrectionEnabledOverride
                ?: globalSettings.contextualCorrectionEnabled,
            taiwanWordingEnabled = profile.taiwanWordingEnabledOverride
                ?: globalSettings.taiwanWordingEnabled,
            smartPunctuationEnabled = profile.smartPunctuationEnabledOverride
                ?: globalSettings.smartPunctuationEnabled,
            terminalPeriodMode = profile.terminalPeriodModeOverride ?: globalSettings.terminalPeriodMode,
            formattingStyle = profile.formattingStyleOverride ?: globalSettings.formattingStyle
        )
        return ResolvedAppVoiceSettings(
            settings = settings,
            formattingStyle = settings.formattingStyle,
            appliedProfile = profile
        )
    }
}

internal object AppVoiceProfileRules {
    fun upsert(profiles: List<AppVoiceProfile>, profile: AppVoiceProfile): List<AppVoiceProfile> {
        val packageName = AppPackageName.normalize(profile.packageName) ?: return profiles
        val existing = profiles.firstOrNull { it.packageName == packageName }
        val normalized = profile.copy(
            packageName = packageName,
            speechModelOverride = profile.speechModelOverride?.takeIf { model ->
                FormattingModels.speech.any { it.id == model }
            },
            providerOverride = profile.providerOverride?.takeIf(TextFormattingProviderRegistry::isProfileSupported),
            createdAt = existing?.createdAt ?: profile.createdAt,
            updatedAt = profile.updatedAt.coerceAtLeast(existing?.updatedAt ?: 0L)
        )
        return profiles.filterNot { it.packageName == packageName } + normalized
    }

    fun setEnabled(profiles: List<AppVoiceProfile>, packageName: String, enabled: Boolean, now: Long): List<AppVoiceProfile> =
        profiles.map { profile ->
            if (profile.packageName == packageName) profile.copy(enabled = enabled, updatedAt = now) else profile
        }

    fun delete(profiles: List<AppVoiceProfile>, packageName: String): List<AppVoiceProfile> =
        profiles.filterNot { it.packageName == packageName }

    fun normalize(profiles: List<AppVoiceProfile>): List<AppVoiceProfile> {
        val normalized = linkedMapOf<String, AppVoiceProfile>()
        profiles.forEach { profile ->
            val packageName = AppPackageName.normalize(profile.packageName) ?: return@forEach
            val safe = profile.copy(
                packageName = packageName,
                speechModelOverride = profile.speechModelOverride?.takeIf { model ->
                    FormattingModels.speech.any { it.id == model }
                },
                providerOverride = profile.providerOverride?.takeIf(TextFormattingProviderRegistry::isProfileSupported)
            )
            val previous = normalized[packageName]
            if (previous == null || safe.updatedAt >= previous.updatedAt) normalized[packageName] = safe
        }
        return normalized.values.toList()
    }
}

internal fun TextFormattingStyle?.displayNameOrInherit(): String =
    this?.displayName ?: "繼承全域"

internal fun SpeechLanguageMode?.displayNameOrInherit(): String =
    this?.displayName ?: "繼承全域"

internal fun TextFormattingProviderId?.displayNameOrInherit(): String =
    this?.displayName ?: "繼承全域"

internal fun TerminalPeriodMode?.displayNameOrInherit(): String = when (this) {
    TerminalPeriodMode.AUTO -> "智慧"
    TerminalPeriodMode.ALWAYS -> "加入"
    TerminalPeriodMode.NEVER -> "不加入"
    null -> "繼承全域"
}

internal fun AppVoiceProfile.summary(): String = listOf(
    speechModelOverride?.let { model -> FormattingModels.speech.firstOrNull { it.id == model }?.displayName }
        ?: "ASR 繼承",
    speechLanguageModeOverride.displayNameOrInherit(),
    smartFormattingEnabledOverride?.let { if (it) "智慧整理開" else "智慧整理關" } ?: "智慧整理繼承",
    providerOverride.displayNameOrInherit(),
    contextualCorrectionEnabledOverride?.let { if (it) "糾錯開" else "糾錯關" } ?: "糾錯繼承",
    taiwanWordingEnabledOverride?.let { if (it) "台灣用字開" else "台灣用字關" } ?: "台灣用字繼承",
    smartPunctuationEnabledOverride?.let { if (it) "智慧標點開" else "智慧標點關" } ?: "智慧標點繼承",
    terminalPeriodModeOverride.displayNameOrInherit(),
    formattingStyleOverride.displayNameOrInherit()
).joinToString(" · ")
