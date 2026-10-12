package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteTest {

    @Test
    fun everyThemeResolvesForEveryAppearanceMode() {
        AppThemeId.entries.forEach { themeId ->
            val light = ThemePaletteRegistry.paletteFor(themeId, ThemeAppearance.LIGHT, systemInDarkTheme = true)
            assertEquals(themeId, light.themeId)
            assertEquals(ThemeAppearance.LIGHT, light.appearance)
            assertFalse("LIGHT 必須是淺色：$themeId", light.isDark)

            val dark = ThemePaletteRegistry.paletteFor(themeId, ThemeAppearance.DARK, systemInDarkTheme = false)
            assertEquals(ThemeAppearance.DARK, dark.appearance)
            assertTrue("DARK 必須是深色：$themeId", dark.isDark)

            // SYSTEM 依系統設定決定。
            assertFalse(
                ThemePaletteRegistry.paletteFor(themeId, ThemeAppearance.SYSTEM, systemInDarkTheme = false).isDark
            )
            assertTrue(
                ThemePaletteRegistry.paletteFor(themeId, ThemeAppearance.SYSTEM, systemInDarkTheme = true).isDark
            )
        }
    }

    @Test
    fun themeIdsUseStableStringsInsteadOfOrdinals() {
        assertEquals("legacy_warm", AppThemeId.LEGACY_WARM.stableId)
        assertEquals("cute_cream", AppThemeId.CUTE_CREAM.stableId)
        assertEquals("industrial_light", AppThemeId.INDUSTRIAL_LIGHT.stableId)
        assertEquals("bright_scifi", AppThemeId.BRIGHT_SCIFI.stableId)
        assertEquals("daily_living", AppThemeId.DAILY_LIVING.stableId)
        assertEquals(5, AppThemeId.entries.size)

        AppThemeId.entries.forEach { themeId ->
            assertEquals(themeId, AppThemeId.fromStableId(themeId.stableId))
            // 允許前後空白，但不得接受未知值。
            assertEquals(themeId, AppThemeId.fromStableId("  ${themeId.stableId}  "))
        }
        assertNull(AppThemeId.fromStableId(null))
        assertNull(AppThemeId.fromStableId(""))
        assertNull(AppThemeId.fromStableId("   "))
        assertNull(AppThemeId.fromStableId("warm"))
        assertNull(AppThemeId.fromStableId("0"))
        assertNull(AppThemeId.fromStableId("LEGACY_WARM"))
    }

    @Test
    fun appearanceIdsUseStableStringsAndFallBackSafely() {
        assertEquals("system", ThemeAppearance.SYSTEM.stableId)
        assertEquals("light", ThemeAppearance.LIGHT.stableId)
        assertEquals("dark", ThemeAppearance.DARK.stableId)
        ThemeAppearance.entries.forEach { appearance ->
            assertEquals(appearance, ThemeAppearance.fromStableId(appearance.stableId))
        }
        assertNull(ThemeAppearance.fromStableId(null))
        assertNull(ThemeAppearance.fromStableId("night"))
        assertEquals(ThemeAppearance.SYSTEM, ThemeDefaultPolicy.resolveAppearance(null))
        assertEquals(ThemeAppearance.SYSTEM, ThemeDefaultPolicy.resolveAppearance("損毀"))
        assertEquals(ThemeAppearance.DARK, ThemeDefaultPolicy.resolveAppearance("dark"))
    }

    @Test
    fun everyPaletteDefinesEveryToken() {
        ThemePaletteRegistry.allPalettes().forEach { palette ->
            val label = "${palette.themeId}/${palette.appearance}"
            listOf(
                palette.background,
                palette.surface,
                palette.surfaceVariant,
                palette.primary,
                palette.primaryBright,
                palette.secondary,
                palette.tertiary,
                palette.onBackground,
                palette.onSurface,
                palette.onSurfaceVariant,
                palette.outline,
                palette.error,
                palette.toolbarBackground,
                palette.toolbarIcon,
                palette.recorderBackground,
                palette.recorderForeground,
                palette.recorderAccent,
                palette.statusIdle,
                palette.statusRecording,
                palette.statusProcessing,
                palette.statusPreview
            ).forEach { token ->
                // 0 代表未定義（全透明黑）；recordingHalo 允許全透明，其他 token 不允許。
                assertNotEquals("$label 有未定義的顏色 token", 0, token)
            }
            listOf(
                palette.modeNormal to "modeNormal",
                palette.modeTranslate to "modeTranslate",
                palette.modeFormat to "modeFormat",
                palette.modeEdit to "modeEdit"
            ).forEach { (accent, name) ->
                assertNotEquals("$label/$name base 未定義", 0, accent.base)
                assertNotEquals("$label/$name bright 未定義", 0, accent.bright)
                assertNotEquals("$label/$name icon 未定義", 0, accent.icon)
                assertNotEquals("$label/$name 底色與亮端不可相同", accent.base, accent.bright)
                assertNotEquals("$label/$name 圖示必須與底色不同", accent.base, accent.icon)
            }
            // 四種模式必須能互相辨識。
            val bases = listOf(
                palette.modeNormal.base,
                palette.modeTranslate.base,
                palette.modeFormat.base,
                palette.modeEdit.base
            )
            assertEquals("$label 的模式色必須互不相同", 4, bases.toSet().size)
            // 圓角與邊框 Token 不影響版面，但必須是合理值。
            assertTrue("$label pill 圓角需大於 0", palette.pillRadiusDp > 0)
            assertTrue("$label 卡片圓角需大於 0", palette.cardRadiusDp > 0)
            assertTrue("$label 邊框寬度不可為負", palette.strokeWidthDp >= 0)
        }
    }

    /**
     * 相容性鐵則：Legacy Warm 的 IME 數值必須與 v0.15.5 的硬編碼常數完全相同。
     * 這裡把舊常數寫成期望值，任何改動都會讓測試失敗。
     */
    @Test
    fun legacyWarmImeTokensStayIdenticalToTheV0155Look() {
        val legacy = ThemePaletteRegistry.imePaletteFor(
            AppThemeId.LEGACY_WARM,
            ThemeAppearance.SYSTEM,
            systemInDarkTheme = false
        )
        assertEquals(0xFF2F2A28.toInt(), legacy.background)
        assertEquals(0xFF3A312E.toInt(), legacy.surface)
        assertEquals(0xFF433936.toInt(), legacy.surfaceVariant)
        assertEquals(0xFF63544E.toInt(), legacy.outline)
        assertEquals(0xFFF3D8C7.toInt(), legacy.primary)
        assertEquals(0xFFF9EBE0.toInt(), legacy.primaryBright)
        assertEquals(0xFFBFD8C1.toInt(), legacy.tertiary)
        assertEquals(0xFFF3AAA4.toInt(), legacy.secondary)
        assertEquals(0xFFF2A7A7.toInt(), legacy.error)
        assertEquals(0xFF35231C.toInt(), legacy.recorderForeground)
        assertEquals(0xFFFFF9F4.toInt(), legacy.onBackground)
        assertEquals(0xFFD1BFB5.toInt(), legacy.onSurfaceVariant)
        assertEquals(0xFF433936.toInt(), legacy.toolbarBackground)
        assertEquals(0xFFD1BFB5.toInt(), legacy.toolbarIcon)
        assertEquals(0xFFE89088.toInt(), legacy.recorderAccent)
        assertEquals(0x00000000, legacy.recordingHalo)
        assertEquals(50, legacy.pillRadiusDp)
        assertEquals(24, legacy.cardRadiusDp)
        assertEquals(1, legacy.strokeWidthDp)
    }

    /** Legacy 的模式色必須與既有的 VoiceModePalette 常數一致（單一來源不會漂移）。 */
    @Test
    fun legacyModeAccentsMatchTheExistingVoiceModePalette() {
        val legacy = ThemePaletteRegistry.paletteFor(AppThemeId.LEGACY_WARM, ThemeAppearance.DARK, false)
        assertEquals(VoiceModePalette.NORMAL_CREAM, legacy.modeNormal.base)
        assertEquals(VoiceModePalette.NORMAL_CREAM_BRIGHT, legacy.modeNormal.bright)
        assertEquals(VoiceModePalette.NORMAL_ICON, legacy.modeNormal.icon)
        assertEquals(VoiceModePalette.TRANSLATE_MINT, legacy.modeTranslate.base)
        assertEquals(VoiceModePalette.TRANSLATE_MINT_BRIGHT, legacy.modeTranslate.bright)
        assertEquals(VoiceModePalette.TRANSLATE_ICON, legacy.modeTranslate.icon)
        assertEquals(VoiceModePalette.FORMAT_ROSE, legacy.modeFormat.base)
        assertEquals(VoiceModePalette.FORMAT_ROSE_BRIGHT, legacy.modeFormat.bright)
        assertEquals(VoiceModePalette.FORMAT_ICON, legacy.modeFormat.icon)
        assertEquals(VoiceModePalette.AI_EDIT_PEACH, legacy.modeEdit.base)
        assertEquals(VoiceModePalette.AI_EDIT_PEACH_BRIGHT, legacy.modeEdit.bright)
        assertEquals(VoiceModePalette.AI_EDIT_ICON, legacy.modeEdit.icon)
        // 一般語音沿用錄音按鈕底色與主色亮端。
        assertEquals(legacy.recorderBackground, legacy.accentForMode(false, false).base)
        assertEquals(legacy.primaryBright, legacy.accentForMode(false, false).bright)
    }

    @Test
    fun legacyImePaletteIgnoresAppearanceWhileNewThemesFollowIt() {
        listOf(ThemeAppearance.SYSTEM, ThemeAppearance.LIGHT, ThemeAppearance.DARK).forEach { appearance ->
            listOf(false, true).forEach { systemDark ->
                val legacy = ThemePaletteRegistry.imePaletteFor(AppThemeId.LEGACY_WARM, appearance, systemDark)
                assertTrue("Legacy 的 IME 面板必須維持深色暖灰棕", legacy.isDark)
                assertEquals(0xFF2F2A28.toInt(), legacy.background)
            }
        }

        val cuteLight = ThemePaletteRegistry.imePaletteFor(AppThemeId.CUTE_CREAM, ThemeAppearance.LIGHT, true)
        assertFalse(cuteLight.isDark)
        val cuteDark = ThemePaletteRegistry.imePaletteFor(AppThemeId.CUTE_CREAM, ThemeAppearance.DARK, false)
        assertTrue(cuteDark.isDark)
        val cuteSystemLight = ThemePaletteRegistry.imePaletteFor(AppThemeId.CUTE_CREAM, ThemeAppearance.SYSTEM, false)
        assertFalse(cuteSystemLight.isDark)
        val cuteSystemDark = ThemePaletteRegistry.imePaletteFor(AppThemeId.CUTE_CREAM, ThemeAppearance.SYSTEM, true)
        assertTrue(cuteSystemDark.isDark)
    }

    @Test
    fun everyPaletteKeepsTextAndIconsReadable() {
        ThemePaletteRegistry.allPalettes().forEach { palette ->
            val label = "${palette.themeId}/${palette.appearance}"
            assertAtLeast(label, "主要文字", palette.onBackground, palette.background, ThemeContrast.NORMAL_TEXT_MIN)
            assertAtLeast(label, "表面文字", palette.onSurface, palette.surface, ThemeContrast.NORMAL_TEXT_MIN)
            assertAtLeast(
                label,
                "錄音按鈕圖示",
                palette.recorderForeground,
                palette.recorderBackground,
                ThemeContrast.NORMAL_TEXT_MIN
            )
            assertAtLeast(
                label,
                "次要文字",
                palette.onSurfaceVariant,
                palette.surface,
                ThemeContrast.LARGE_TEXT_MIN
            )
            assertAtLeast(label, "工具列圖示", palette.toolbarIcon, palette.toolbarBackground, ThemeContrast.LARGE_TEXT_MIN)
            listOf(
                palette.modeNormal,
                palette.modeTranslate,
                palette.modeFormat,
                palette.modeEdit
            ).forEach { accent ->
                assertAtLeast(label, "模式圖示", accent.icon, accent.base, ThemeContrast.LARGE_TEXT_MIN)
            }
        }
    }

    @Test
    fun contrastRatioMatchesKnownExtremes() {
        assertEquals(21.0, ThemeContrast.contrastRatio(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.01)
        assertEquals(1.0, ThemeContrast.contrastRatio(0xFF123456.toInt(), 0xFF123456.toInt()), 0.001)
        assertTrue(
            ThemeContrast.contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()) ==
                ThemeContrast.contrastRatio(0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        )
    }

    @Test
    fun everyThemeExposesBothAppearancesForTheSettingsPreview() {
        assertEquals(AppThemeId.entries.size * 2, ThemePaletteRegistry.allPalettes().size)
        AppThemeId.entries.forEach { themeId ->
            val palettes = ThemePaletteRegistry.allPalettes().filter { it.themeId == themeId }
            assertEquals(2, palettes.size)
            assertEquals(setOf(false, true), palettes.map { it.isDark }.toSet())
        }
    }

    private fun assertAtLeast(label: String, what: String, foreground: Int, background: Int, minimum: Double) {
        val ratio = ThemeContrast.contrastRatio(foreground, background)
        assertTrue(
            "$label 的$what 對比不足：%.2f < %.1f".format(ratio, minimum),
            ratio >= minimum
        )
    }
}
