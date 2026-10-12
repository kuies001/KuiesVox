package tw.kuies.voiceime

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kuies.voiceime.ui.theme.LegacyVoiceImeShapes
import tw.kuies.voiceime.ui.theme.colorSchemeFor
import tw.kuies.voiceime.ui.theme.legacyDarkColorScheme
import tw.kuies.voiceime.ui.theme.legacyLightColorScheme
import tw.kuies.voiceime.ui.theme.shapesFor
import tw.kuies.voiceime.ui.theme.WarmBgDark
import tw.kuies.voiceime.ui.theme.WarmBgLight
import tw.kuies.voiceime.ui.theme.WarmErrorContainerDark
import tw.kuies.voiceime.ui.theme.WarmErrorLight
import tw.kuies.voiceime.ui.theme.WarmOutlineDark
import tw.kuies.voiceime.ui.theme.WarmPrimaryContainerLight
import tw.kuies.voiceime.ui.theme.WarmPrimaryDark
import tw.kuies.voiceime.ui.theme.WarmPrimaryLight
import tw.kuies.voiceime.ui.theme.WarmSurfaceVariantDark
import tw.kuies.voiceime.ui.theme.WarmTextPrimaryLight
import tw.kuies.voiceime.ui.theme.WarmTextSecondaryDark

class ThemeComposeMappingTest {

    private val newThemes = listOf(
        AppThemeId.CUTE_CREAM,
        AppThemeId.INDUSTRIAL_LIGHT,
        AppThemeId.BRIGHT_SCIFI,
        AppThemeId.DAILY_LIVING
    )

    @Test
    fun everySharedTokenMapsToTheSameColourInCompose() {
        newThemes.forEach { themeId ->
            listOf(ThemeAppearance.LIGHT, ThemeAppearance.DARK).forEach { appearance ->
                val palette = ThemePaletteRegistry.paletteFor(themeId, appearance, systemInDarkTheme = false)
                val scheme = colorSchemeFor(palette)
                val label = "$themeId/$appearance"

                assertEquals(label, Color(palette.background), scheme.background)
                assertEquals(label, Color(palette.surface), scheme.surface)
                assertEquals(label, Color(palette.surfaceVariant), scheme.surfaceVariant)
                assertEquals(label, Color(palette.primary), scheme.primary)
                assertEquals(label, Color(palette.secondary), scheme.secondary)
                assertEquals(label, Color(palette.tertiary), scheme.tertiary)
                assertEquals(label, Color(palette.onBackground), scheme.onBackground)
                assertEquals(label, Color(palette.onSurface), scheme.onSurface)
                assertEquals(label, Color(palette.onSurfaceVariant), scheme.onSurfaceVariant)
                assertEquals(label, Color(palette.outline), scheme.outline)
                assertEquals(label, Color(palette.error), scheme.error)
                // 中央錄音按鈕的圖示色同時是 primary 上的前景色。
                assertEquals(label, Color(palette.recorderForeground), scheme.onPrimary)
                // 容器的亮端就是主色的亮端，預覽與實際共用同一個 token。
                assertEquals(label, Color(palette.primaryBright), scheme.primaryContainer)
            }
        }
    }

    @Test
    fun composeTextStaysReadableOnTheMappedScheme() {
        newThemes.forEach { themeId ->
            listOf(ThemeAppearance.LIGHT, ThemeAppearance.DARK).forEach { appearance ->
                val palette = ThemePaletteRegistry.paletteFor(themeId, appearance, systemInDarkTheme = false)
                val scheme = colorSchemeFor(palette)
                val body = ThemeContrast.contrastRatio(
                    palette.onBackground,
                    scheme.background.toArgb()
                )
                assertTrue(
                    "$themeId/$appearance 的 body 對比不足：$body",
                    body >= ThemeContrast.NORMAL_TEXT_MIN
                )
                val onPrimary = ThemeContrast.contrastRatio(
                    scheme.onPrimary.toArgb(),
                    scheme.primary.toArgb()
                )
                assertTrue("$themeId/$appearance 的 onPrimary 對比不足：$onPrimary", onPrimary >= 4.5)
                val onSecondary = ThemeContrast.contrastRatio(
                    scheme.onSecondary.toArgb(),
                    scheme.secondary.toArgb()
                )
                assertTrue("$themeId/$appearance 的 onSecondary 對比不足：$onSecondary", onSecondary >= 4.5)
                val onTertiary = ThemeContrast.contrastRatio(
                    scheme.onTertiary.toArgb(),
                    scheme.tertiary.toArgb()
                )
                assertTrue("$themeId/$appearance 的 onTertiary 對比不足：$onTertiary", onTertiary >= 4.5)
            }
        }
    }

    /** Legacy Warm 的 App 設定畫面必須維持 v0.15.5 的色票。 */
    @Test
    fun legacyWarmKeepsTheV0155ComposeSchemes() {
        assertEquals(WarmBgLight, legacyLightColorScheme.background)
        assertEquals(WarmPrimaryLight, legacyLightColorScheme.primary)
        assertEquals(WarmTextPrimaryLight, legacyLightColorScheme.onBackground)
        assertEquals(WarmPrimaryContainerLight, legacyLightColorScheme.primaryContainer)
        assertEquals(WarmErrorLight, legacyLightColorScheme.error)

        assertEquals(WarmBgDark, legacyDarkColorScheme.background)
        assertEquals(WarmPrimaryDark, legacyDarkColorScheme.primary)
        assertEquals(WarmSurfaceVariantDark, legacyDarkColorScheme.surfaceVariant)
        assertEquals(WarmTextSecondaryDark, legacyDarkColorScheme.onSurfaceVariant)
        assertEquals(WarmOutlineDark, legacyDarkColorScheme.outline)
        assertEquals(WarmErrorContainerDark, legacyDarkColorScheme.errorContainer)
    }

    @Test
    fun legacyWarmKeepsTheOriginalShapesWhileOtherThemesUseTheirOwnRadius() {
        val legacyPalette = ThemePaletteRegistry.paletteFor(AppThemeId.LEGACY_WARM, ThemeAppearance.DARK, false)
        assertSame(LegacyVoiceImeShapes, shapesFor(AppThemeId.LEGACY_WARM, legacyPalette))
        assertEquals(RoundedCornerShape(8.dp), LegacyVoiceImeShapes.extraSmall)
        assertEquals(RoundedCornerShape(12.dp), LegacyVoiceImeShapes.small)
        assertEquals(RoundedCornerShape(16.dp), LegacyVoiceImeShapes.medium)
        assertEquals(RoundedCornerShape(20.dp), LegacyVoiceImeShapes.large)
        assertEquals(RoundedCornerShape(28.dp), LegacyVoiceImeShapes.extraLarge)

        newThemes.forEach { themeId ->
            val palette = ThemePaletteRegistry.paletteFor(themeId, ThemeAppearance.LIGHT, false)
            val shapes = shapesFor(themeId, palette)
            val card = palette.cardRadiusDp
            // 形狀必須由該主題的圓角 Token 推導，而不是共用 Legacy 的數值。
            assertEquals("$themeId 的 medium 圓角", RoundedCornerShape(card.dp), shapes.medium)
            assertEquals(
                "$themeId 的 extraSmall 圓角",
                RoundedCornerShape((card * 0.5f).toInt().dp),
                shapes.extraSmall
            )
            assertTrue("$themeId 的圓角不可為負", card > 0)
            assertTrue("$themeId 的 pill 圓角不可為負", palette.pillRadiusDp > 0)
        }
        // 幾何風（Industrial）與圓潤風（Cute）必須明顯不同。
        val industrial = shapesFor(
            AppThemeId.INDUSTRIAL_LIGHT,
            ThemePaletteRegistry.paletteFor(AppThemeId.INDUSTRIAL_LIGHT, ThemeAppearance.LIGHT, false)
        )
        val cute = shapesFor(
            AppThemeId.CUTE_CREAM,
            ThemePaletteRegistry.paletteFor(AppThemeId.CUTE_CREAM, ThemeAppearance.LIGHT, false)
        )
        assertNotEquals(industrial.medium, cute.medium)
        assertNotEquals(LegacyVoiceImeShapes.medium, industrial.medium)
    }

    /** 測試 11：設定畫面預覽與實際 Theme Tokens 必須一致。 */
    @Test
    fun previewSpecIsBuiltFromTheSameTokensAsTheRealScreens() {
        AppThemeId.entries.forEach { themeId ->
            listOf(ThemeAppearance.LIGHT, ThemeAppearance.DARK).forEach { appearance ->
                val palette = ThemePaletteRegistry.paletteFor(themeId, appearance, systemInDarkTheme = false)
                val spec = ThemePreview.specFor(themeId, appearance, systemInDarkTheme = false)
                val label = "$themeId/$appearance"

                assertEquals(label, ThemePreviewSpec.of(palette), spec)
                assertEquals(label, palette.background, spec.imeBackground)
                assertEquals(label, palette.toolbarBackground, spec.toolbarBackground)
                assertEquals(label, palette.toolbarIcon, spec.toolbarIcon)
                assertEquals(label, palette.recorderBackground, spec.recorderBackground)
                assertEquals(label, palette.recorderForeground, spec.recorderForeground)
                assertEquals(label, palette.recorderAccent, spec.recorderAccent)
                assertEquals(label, palette.recordingHalo, spec.recordingHalo)
                assertEquals(
                    label,
                    listOf(palette.primary, palette.secondary, palette.tertiary, palette.surfaceVariant, palette.outline),
                    spec.swatches
                )
                assertEquals(
                    label,
                    listOf(
                        palette.modeNormal.base,
                        palette.modeTranslate.base,
                        palette.modeFormat.base,
                        palette.modeEdit.base
                    ),
                    spec.modeAccents
                )
            }
        }
    }

    @Test
    fun systemAppearancePreviewFollowsTheSystemDarkSetting() {
        val light = ThemePreview.specFor(AppThemeId.DAILY_LIVING, ThemeAppearance.SYSTEM, systemInDarkTheme = false)
        val dark = ThemePreview.specFor(AppThemeId.DAILY_LIVING, ThemeAppearance.SYSTEM, systemInDarkTheme = true)
        assertNotEquals(light, dark)
        assertEquals(
            ThemePaletteRegistry.paletteFor(AppThemeId.DAILY_LIVING, ThemeAppearance.SYSTEM, false).background,
            light.imeBackground
        )
        assertEquals(
            ThemePaletteRegistry.paletteFor(AppThemeId.DAILY_LIVING, ThemeAppearance.SYSTEM, true).background,
            dark.imeBackground
        )
    }
}
